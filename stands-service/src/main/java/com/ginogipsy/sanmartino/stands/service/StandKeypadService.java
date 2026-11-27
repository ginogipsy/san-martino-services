package com.ginogipsy.sanmartino.stands.service;

import com.ginogipsy.sanmartino.observability.audit.AuditUserResolver;
import com.ginogipsy.sanmartino.observability.audit.AuditUserResolver.AuditUser;
import com.ginogipsy.sanmartino.observability.audit.Auditable;
import com.ginogipsy.sanmartino.stands.StandsConfig;
import com.ginogipsy.sanmartino.stands.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StandKeypadService {

    public static final int MAX_SLOTS = 10;

    private static final List<StandOrderStatus> ACTIVE_STATUSES = List.of(
            StandOrderStatus.IN_PROGRESS,
            StandOrderStatus.READY
    );

    private final StandRepository standRepository;
    private final StandKeypadConfigRepository configRepository;
    private final StandKeypadSlotRepository slotRepository;
    private final StandStaffPermissionRepository permissionRepository;
    private final StandOrderRepository orderRepository;
    private final AuditUserResolver auditUserResolver;

    @Autowired
    public StandKeypadService(
            StandRepository standRepository,
            StandKeypadConfigRepository configRepository,
            StandKeypadSlotRepository slotRepository,
            StandStaffPermissionRepository permissionRepository,
            StandOrderRepository orderRepository,
            AuditUserResolver auditUserResolver
    ) {
        this.standRepository = standRepository;
        this.configRepository = configRepository;
        this.slotRepository = slotRepository;
        this.permissionRepository = permissionRepository;
        this.orderRepository = orderRepository;
        this.auditUserResolver = auditUserResolver;
    }

    private static Set<String> extractJwtRolesFromRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return Set.of();
        }
        HttpServletRequest request = attrs.getRequest();
        if (request == null) {
            return Set.of();
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Set.of();
        }
        try {
            String token = authHeader.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Set.of();
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Set<String> found = new HashSet<>();
            for (String candidate : List.of("ADMIN", "ROLE_ADMIN", "MEZZ", "ROLE_MEZZ", "STAND_OWNER", "ROLE_STAND_OWNER")) {
                if (payloadJson.contains("\"" + candidate + "\"")) {
                    found.add(candidate);
                }
            }
            return found;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static String generateDeviceToken(int standNumber) {
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "ESP32-CANTINA-%02d-%s", standNumber, randomPart);
    }

    @Transactional(readOnly = true)
    @Cacheable(StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS)
    public Map<UUID, StandQueueSnapshot> loadQueueSnapshotsByStandId() {
        if (orderRepository == null) {
            return Map.of();
        }
        List<StandOrderEntity> activeOrders = orderRepository.findAllByStatusIn(ACTIVE_STATUSES);
        Map<UUID, List<StandOrderEntity>> byStand = activeOrders.stream()
                .filter(o -> o.getStandId() != null)
                .collect(Collectors.groupingBy(StandOrderEntity::getStandId));

        Map<UUID, StandQueueSnapshot> result = new HashMap<>();
        for (Map.Entry<UUID, List<StandOrderEntity>> entry : byStand.entrySet()) {
            List<StandOrderEntity> orders = entry.getValue();
            int count = orders.size();
            int currentOrderNumber = orders.stream()
                    .mapToInt(StandOrderEntity::getOrderNumber)
                    .min()
                    .orElse(0);
            int totalPendingFood = orders.stream()
                    .flatMap(o -> o.getItems().stream())
                    .filter(it -> it.getKind() == MenuKind.FOOD)
                    .mapToInt(it -> Math.max(0, it.getOrderedQuantity() - it.getCookedQuantity()))
                    .sum();
            int estimatedMinutes = count == 0 ? 0 : Math.max(2, count * 2 + totalPendingFood);
            result.put(entry.getKey(), new StandQueueSnapshot(count, currentOrderNumber, estimatedMinutes));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AccessibleStandView> listAccessibleStands() {
        List<StandEntity> stands = standRepository.findAllByOrderByNumberAsc();
        Map<UUID, StandQueueSnapshot> snapshots = loadQueueSnapshotsByStandId();

        List<AccessibleStandView> accessible = new ArrayList<>();
        for (StandEntity stand : stands) {
            stand.getOwners().size();
            List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, null);
            if (!perms.isEmpty()) {
                StandQueueSnapshot snap = snapshots.getOrDefault(stand.getId(), StandQueueSnapshot.EMPTY);
                accessible.add(new AccessibleStandView(
                        stand.getId(),
                        stand.getNumber(),
                        stand.getName(),
                        perms,
                        snap.activeOrdersCount(),
                        snap.currentServingOrderNumber()
                ));
            }
        }
        return accessible;
    }

    @Transactional
    public KeypadStateView getKeypadState(UUID standId, KeypadStationRole station, String deviceToken) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);

        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, deviceToken, config);
        if (perms.isEmpty()) {
            throw new StandAccessDeniedException(
                    "Non hai i permessi per accedere alla pulsantiera o agli ordini della cantina #" + stand.getNumber()
            );
        }

        if (station != null) {
            Instant now = Instant.now();
            switch (station) {
                case CASHIER -> config.setLastCashierSeenAt(now);
                case KITCHEN -> config.setLastKitchenSeenAt(now);
                case DELIVERY -> config.setLastDeliverySeenAt(now);
            }
            config.setUpdatedAt(now);
            configRepository.save(config);
        }

        return buildKeypadStateView(stand, config, perms);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true)
    })
    @Auditable(
            action = "STAND:KEYPAD_SLOTS_UPDATE",
            description = "'Aggiornamento 10 slot pulsantiera cantina: ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView updateSlots(
            UUID standId,
            List<SlotAssignmentInput> assignments,
            boolean regenerateDeviceToken
    ) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, null, config);
        requirePermission(perms, StandStaffRole.OWNER, "Solo il proprietario della cantina o un amministratore può configurare i 10 slot.");

        Map<UUID, MenuItemEntity> standItemsById = stand.getMenuItems().stream()
                .filter(it -> it.getId() != null)
                .collect(Collectors.toMap(MenuItemEntity::getId, it -> it, (a, b) -> a));

        Map<Integer, UUID> requestedBySlot = new HashMap<>();
        if (assignments != null) {
            for (SlotAssignmentInput input : assignments) {
                if (input.slotNumber() < 1 || input.slotNumber() > MAX_SLOTS) {
                    throw new StandKeypadValidationException("Il numero di slot deve essere compreso tra 1 e " + MAX_SLOTS);
                }
                if (input.menuItemId() != null && !standItemsById.containsKey(input.menuItemId())) {
                    throw new MenuItemNotFoundException(standId, input.menuItemId());
                }
                requestedBySlot.put(input.slotNumber(), input.menuItemId());
            }
        }

        Instant now = Instant.now();
        List<StandKeypadSlotEntity> existingSlots = slotRepository.findAllByStandIdOrderBySlotNumberAsc(standId);
        Map<Integer, StandKeypadSlotEntity> existingBySlot = existingSlots.stream()
                .collect(Collectors.toMap(StandKeypadSlotEntity::getSlotNumber, s -> s, (a, b) -> a));

        for (int slotNum = 1; slotNum <= MAX_SLOTS; slotNum++) {
            StandKeypadSlotEntity slotEntity = existingBySlot.get(slotNum);
            UUID targetMenuItemId = requestedBySlot.containsKey(slotNum)
                    ? requestedBySlot.get(slotNum)
                    : (slotEntity != null ? slotEntity.getMenuItemId() : null);

            if (slotEntity == null) {
                slotEntity = new StandKeypadSlotEntity(
                        UUID.randomUUID(),
                        standId,
                        slotNum,
                        targetMenuItemId,
                        now
                );
            } else {
                slotEntity.setMenuItemId(targetMenuItemId);
                slotEntity.setUpdatedAt(now);
            }
            slotRepository.save(slotEntity);
        }

        if (regenerateDeviceToken) {
            config.setDeviceToken(generateDeviceToken(stand.getNumber()));
            config.setUpdatedAt(now);
            configRepository.save(config);
        }

        return buildKeypadStateView(stand, config, perms);
    }

    @Transactional
    @Auditable(
            action = "STAND:STAFF_PERMISSION_ADD",
            description = "'Assegnazione ruolo ' + #permission + ' a ' + #username + ' su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView addStaffPermission(
            UUID standId,
            String username,
            StandStaffRole permission
    ) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, null, config);
        requirePermission(perms, StandStaffRole.OWNER, "Solo il proprietario della cantina o un amministratore può gestire i permessi delle postazioni.");

        if (username == null || username.isBlank() || permission == null) {
            throw new StandKeypadValidationException("Username e ruolo di postazione sono obbligatori.");
        }
        String cleanUsername = username.trim().toLowerCase(Locale.ROOT);
        AuditUser currentUser = resolveAuditUser();
        String grantedBy = currentUser != null && currentUser.username() != null ? currentUser.username() : "owner";

        if (permissionRepository.findByStandIdAndUsernameIgnoreCaseAndPermission(standId, cleanUsername, permission).isEmpty()) {
            StandStaffPermissionEntity entity = new StandStaffPermissionEntity(
                    UUID.randomUUID(),
                    standId,
                    cleanUsername,
                    permission,
                    grantedBy,
                    Instant.now()
            );
            permissionRepository.save(entity);
        }

        List<StandStaffRole> updatedPerms = resolveUserPermissionsForStand(stand, null, config);
        return buildKeypadStateView(stand, config, updatedPerms);
    }

    @Transactional
    @Auditable(
            action = "STAND:STAFF_PERMISSION_DELETE",
            description = "'Revoca permesso postazione ' + #permissionId + ' su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView deleteStaffPermission(UUID standId, UUID permissionId) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, null, config);
        requirePermission(perms, StandStaffRole.OWNER, "Solo il proprietario della cantina o un amministratore può revocare i permessi delle postazioni.");

        permissionRepository.findById(permissionId).ifPresent(entity -> {
            if (standId.equals(entity.getStandId())) {
                permissionRepository.delete(entity);
            }
        });

        List<StandStaffRole> updatedPerms = resolveUserPermissionsForStand(stand, null, config);
        return buildKeypadStateView(stand, config, updatedPerms);
    }

    /**
     * Postazione CASSA (Web o ESP32 #1):
     * Conferma un nuovo ordine composto da 1 o più slot (es. Slot 1 x2 + Slot 7 x1).
     * Decrementa atomicamente `availablePlates` sui piatti del menu, assegna il numero
     * progressivo d'ordine `#N` e accoda l'ordine per Cucina (ESP32 #2) e Consegna (ESP32 #3).
     * Le bevande (`DRINK`) sono già pronte al bancone (`cookedQuantity = orderedQuantity`),
     * mentre i piatti (`FOOD`) richiedono la pressione dei pulsanti in Cucina.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true)
    })
    @Auditable(
            action = "STAND:KEYPAD_ORDER_CREATE",
            description = "'Nuovo ordine cassa su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView createOrder(
            UUID standId,
            List<OrderItemInput> rawItems,
            String deviceToken
    ) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, deviceToken, config);
        requirePermission(perms, StandStaffRole.CASHIER, "Permesso CASHIER (Postazione Cassa) richiesto per inserire ordini.");

        if (rawItems == null || rawItems.isEmpty()) {
            throw new StandKeypadValidationException("Seleziona almeno un piatto o bevanda sulla pulsantiera prima di confermare l'ordine.");
        }

        // Aggrega eventuali pressioni multiple dello stesso slot (es. Piatto 1 + Piatto 1 + Vino)
        Map<Integer, Integer> aggregatedBySlot = new LinkedHashMap<>();
        for (OrderItemInput input : rawItems) {
            if (input.slotNumber() < 1 || input.slotNumber() > MAX_SLOTS) {
                throw new StandKeypadValidationException("Slot non valido: " + input.slotNumber() + " (ammessi 1..10).");
            }
            if (input.quantity() <= 0) {
                continue;
            }
            aggregatedBySlot.merge(input.slotNumber(), input.quantity(), Integer::sum);
        }

        if (aggregatedBySlot.isEmpty()) {
            throw new StandKeypadValidationException("L'ordine non contiene quantità valide.");
        }

        Map<Integer, StandKeypadSlotEntity> slotsByNumber = slotRepository.findAllByStandIdOrderBySlotNumberAsc(standId)
                .stream()
                .collect(Collectors.toMap(StandKeypadSlotEntity::getSlotNumber, s -> s, (a, b) -> a));

        Map<UUID, MenuItemEntity> menuItemsById = stand.getMenuItems().stream()
                .filter(it -> it.getId() != null)
                .collect(Collectors.toMap(MenuItemEntity::getId, it -> it, (a, b) -> a));

        // Validazione disponibilità prima di decrementare
        for (Map.Entry<Integer, Integer> entry : aggregatedBySlot.entrySet()) {
            int slotNum = entry.getKey();
            int qty = entry.getValue();
            StandKeypadSlotEntity slot = slotsByNumber.get(slotNum);
            if (slot == null || slot.getMenuItemId() == null) {
                throw new StandKeypadValidationException("Lo Slot " + slotNum + " non è associato ad alcun piatto o bevanda.");
            }
            MenuItemEntity menuItem = menuItemsById.get(slot.getMenuItemId());
            if (menuItem == null) {
                throw new StandKeypadValidationException("Il piatto associato allo Slot " + slotNum + " non esiste più nel menu.");
            }
            int available = menuItem.getAvailablePlates() != null ? menuItem.getAvailablePlates() : 0;
            if (available < qty) {
                throw new StandKeypadValidationException(
                        "Porzioni insufficienti per \"" + menuItem.getName() + "\" (Slot " + slotNum + "): richieste "
                                + qty + ", disponibili " + available + "."
                );
            }
        }

        Instant now = Instant.now();
        int assignedOrderNumber = config.getNextOrderNumber();
        config.setNextOrderNumber(assignedOrderNumber + 1);
        config.setLastCashierSeenAt(now);
        config.setUpdatedAt(now);
        configRepository.save(config);

        AuditUser currentUser = resolveAuditUser();
        String createdBy = (deviceToken != null && !deviceToken.isBlank())
                ? "esp32-cassa"
                : (currentUser != null && currentUser.username() != null ? currentUser.username() : "cassa");

        StandOrderEntity order = new StandOrderEntity(
                UUID.randomUUID(),
                standId,
                assignedOrderNumber,
                StandOrderStatus.IN_PROGRESS,
                createdBy,
                now
        );

        boolean allItemsCooked = true;
        for (Map.Entry<Integer, Integer> entry : aggregatedBySlot.entrySet()) {
            int slotNum = entry.getKey();
            int qty = entry.getValue();
            StandKeypadSlotEntity slot = slotsByNumber.get(slotNum);
            MenuItemEntity menuItem = menuItemsById.get(slot.getMenuItemId());

            int currentAvailable = menuItem.getAvailablePlates() != null ? menuItem.getAvailablePlates() : 0;
            menuItem.setAvailablePlates(Math.max(0, currentAvailable - qty));

            // Le bevande (DRINK) sono servite direttamente dal bancone consegna senza attesa cucina;
            // i piatti (FOOD) partono con cookedQuantity = 0 e accendono il LED sulla postazione Cucina.
            int initialCooked = (menuItem.getKind() == MenuKind.DRINK) ? qty : 0;
            if (initialCooked < qty) {
                allItemsCooked = false;
            }

            StandOrderItemEntity orderItem = new StandOrderItemEntity(
                    UUID.randomUUID(),
                    slotNum,
                    menuItem.getId(),
                    menuItem.getName(),
                    menuItem.getKind(),
                    qty,
                    initialCooked,
                    0
            );
            order.addItem(orderItem);
        }

        if (allItemsCooked) {
            order.setStatus(StandOrderStatus.READY);
            order.setReadyAt(now);
        }

        orderRepository.save(order);
        return buildKeypadStateView(stand, config, perms);
    }

    /**
     * Postazione CUCINA (Web o ESP32 #2):
     * L'operatore in cucina preme il pulsante dello Slot K (che ha il LED acceso)
     * ogni volta che termina di impiattare una porzione di quel piatto.
     * Incrementa `cookedQuantity` sull'ordine attivo più vecchio che richiede ancora lo Slot K.
     * Quando tutte le porzioni in coda per lo Slot K sono state preparate, il LED K si spegne.
     */
    @Transactional
    @CacheEvict(value = StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS, allEntries = true)
    @Auditable(
            action = "STAND:KEYPAD_KITCHEN_PRESS",
            description = "'Cucina: piatto pronto slot ' + #slotNumber + ' (x' + #quantity + ') su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView pressKitchenSlot(
            UUID standId,
            int slotNumber,
            int quantity,
            String deviceToken
    ) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, deviceToken, config);
        requirePermission(perms, StandStaffRole.KITCHEN, "Permesso KITCHEN (Postazione Cucina) richiesto.");

        if (slotNumber < 1 || slotNumber > MAX_SLOTS) {
            throw new StandKeypadValidationException("Slot non valido: " + slotNumber);
        }

        int remainingToMark = Math.max(1, quantity);
        Instant now = Instant.now();
        config.setLastKitchenSeenAt(now);
        config.setUpdatedAt(now);
        configRepository.save(config);

        List<StandOrderEntity> activeOrders = orderRepository.findAllByStandIdAndStatusInOrderByOrderNumberAsc(
                standId,
                ACTIVE_STATUSES
        );

        for (StandOrderEntity order : activeOrders) {
            if (remainingToMark <= 0) break;
            boolean orderModified = false;
            for (StandOrderItemEntity item : order.getItems()) {
                if (item.getSlotNumber() == slotNumber && item.getCookedQuantity() < item.getOrderedQuantity()) {
                    int needed = item.getOrderedQuantity() - item.getCookedQuantity();
                    int delta = Math.min(needed, remainingToMark);
                    item.setCookedQuantity(item.getCookedQuantity() + delta);
                    remainingToMark -= delta;
                    orderModified = true;
                }
            }
            if (orderModified) {
                boolean isOrderFullyCooked = order.getItems().stream()
                        .allMatch(it -> it.getCookedQuantity() >= it.getOrderedQuantity());
                if (isOrderFullyCooked && order.getStatus() == StandOrderStatus.IN_PROGRESS) {
                    order.setStatus(StandOrderStatus.READY);
                    order.setReadyAt(now);
                }
                orderRepository.save(order);
            }
        }

        return buildKeypadStateView(stand, config, perms);
    }

    /**
     * Postazione CONSEGNA / BANCONE (Web o ESP32 #3):
     * Il display numerico mostra il numero del prossimo ordine in uscita (es. Ordine #1).
     * I LED accesi sulla pulsantiera del consegnatore corrispondono agli slot richiesti dall'Ordine #1
     * che non sono ancora stati completamente messi sul vassoio (`deliveredQuantity < orderedQuantity`).
     * - Esempio: Ordine #1 = 2x Piatto 1 (Slot 1) + 1x Vino (Slot 7).
     * - Il consegnatore ha accesi LED 1 e LED 7.
     * - Preme Pulsante 1 (1a porzione): `deliveredQuantity` diventa 1/2 -> LED 1 resta ACCESO.
     * - Preme di nuovo Pulsante 1 (2a porzione): `deliveredQuantity` diventa 2/2 -> LED 1 si SPEGNE.
     * - Preme Pulsante 7 (Vino): `deliveredQuantity` diventa 1/1 -> LED 7 si SPEGNE.
     * - Non appena tutti gli item dell'Ordine #1 sono stati premuti (`deliveredQuantity == orderedQuantity`),
     * l'Ordine #1 diventa `DELIVERED` e il display + i LED passano automaticamente all'Ordine #2!
     */
    @Transactional
    @CacheEvict(value = StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS, allEntries = true)
    @Auditable(
            action = "STAND:KEYPAD_DELIVERY_PRESS",
            description = "'Consegna: servito slot ' + #slotNumber + ' (x' + #quantity + ') su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView pressDeliverySlot(
            UUID standId,
            int slotNumber,
            int quantity,
            String deviceToken
    ) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, deviceToken, config);
        requirePermission(perms, StandStaffRole.DELIVERY, "Permesso DELIVERY (Postazione Consegna) richiesto.");

        if (slotNumber < 1 || slotNumber > MAX_SLOTS) {
            throw new StandKeypadValidationException("Slot non valido: " + slotNumber);
        }

        Instant now = Instant.now();
        config.setLastDeliverySeenAt(now);
        config.setUpdatedAt(now);
        configRepository.save(config);

        List<StandOrderEntity> activeOrders = orderRepository.findAllByStandIdAndStatusInOrderByOrderNumberAsc(
                standId,
                ACTIVE_STATUSES
        );

        if (activeOrders.isEmpty()) {
            return buildKeypadStateView(stand, config, perms);
        }

        // L'ordine corrente in uscita è sempre il primo della coda attiva (ordine #N più vecchio non ancora consegnato)
        StandOrderEntity currentOrder = activeOrders.get(0);
        int remainingToDeliver = Math.max(1, quantity);
        boolean modified = false;

        for (StandOrderItemEntity item : currentOrder.getItems()) {
            if (item.getSlotNumber() == slotNumber && item.getDeliveredQuantity() < item.getOrderedQuantity()) {
                int needed = item.getOrderedQuantity() - item.getDeliveredQuantity();
                int delta = Math.min(needed, remainingToDeliver);
                int newDelivered = item.getDeliveredQuantity() + delta;
                item.setDeliveredQuantity(newDelivered);
                // Se il consegnatore serve direttamente un piatto al bancone prima del click cucina, allinea anche cookedQuantity
                if (item.getCookedQuantity() < newDelivered) {
                    item.setCookedQuantity(newDelivered);
                }
                remainingToDeliver -= delta;
                modified = true;
            }
        }

        if (modified) {
            boolean allDelivered = currentOrder.getItems().stream()
                    .allMatch(it -> it.getDeliveredQuantity() >= it.getOrderedQuantity());
            if (allDelivered) {
                currentOrder.setStatus(StandOrderStatus.DELIVERED);
                if (currentOrder.getReadyAt() == null) {
                    currentOrder.setReadyAt(now);
                }
                currentOrder.setDeliveredAt(now);
            } else {
                boolean allCooked = currentOrder.getItems().stream()
                        .allMatch(it -> it.getCookedQuantity() >= it.getOrderedQuantity());
                if (allCooked && currentOrder.getStatus() == StandOrderStatus.IN_PROGRESS) {
                    currentOrder.setStatus(StandOrderStatus.READY);
                    currentOrder.setReadyAt(now);
                }
            }
            orderRepository.save(currentOrder);
        }

        return buildKeypadStateView(stand, config, perms);
    }

    @Transactional
    @CacheEvict(value = StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS, allEntries = true)
    @Auditable(
            action = "STAND:KEYPAD_RESET_COUNTER",
            description = "'Reset contatore ordini e coda su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView resetOrderCounter(UUID standId) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, null, config);
        requirePermission(perms, StandStaffRole.OWNER, "Solo il proprietario della cantina o un amministratore può azzerare il contatore ordini.");

        Instant now = Instant.now();
        List<StandOrderEntity> activeOrders = orderRepository.findAllByStandIdAndStatusInOrderByOrderNumberAsc(
                standId,
                ACTIVE_STATUSES
        );
        for (StandOrderEntity order : activeOrders) {
            order.setStatus(StandOrderStatus.CANCELLED);
            orderRepository.save(order);
        }

        config.setNextOrderNumber(1);
        config.setUpdatedAt(now);
        configRepository.save(config);

        return buildKeypadStateView(stand, config, perms);
    }

    /**
     * Annulla un singolo ordine attivo in coda (es. errore di battitura in cassa o cliente che cambia idea)
     * e ripristina automaticamente le porzioni (`availablePlates`) sui rispettivi piatti/vini del menu.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STANDS_LIST, allEntries = true),
            @CacheEvict(value = StandsConfig.CACHE_STAND_DETAIL, allEntries = true)
    })
    @Auditable(
            action = "STAND:KEYPAD_ORDER_CANCEL",
            description = "'Annullamento ordine ' + #orderId + ' con ripristino porzioni su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView cancelOrder(UUID standId, UUID orderId) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, null, config);
        requirePermission(perms, StandStaffRole.CASHIER, "Permesso CASHIER o OWNER richiesto per annullare un ordine.");

        StandOrderEntity order = orderRepository.findById(orderId)
                .filter(o -> standId.equals(o.getStandId()))
                .orElseThrow(() -> new StandKeypadValidationException("Ordine non trovato per questa cantina."));

        if (order.getStatus() == StandOrderStatus.CANCELLED) {
            return buildKeypadStateView(stand, config, perms);
        }
        if (order.getStatus() == StandOrderStatus.DELIVERED) {
            throw new StandKeypadValidationException(
                    "L'ordine #" + order.getOrderNumber() + " è già stato consegnato e non può essere annullato."
            );
        }

        Map<UUID, MenuItemEntity> menuItemsById = stand.getMenuItems().stream()
                .filter(it -> it.getId() != null)
                .collect(Collectors.toMap(MenuItemEntity::getId, it -> it, (a, b) -> a));

        for (StandOrderItemEntity item : order.getItems()) {
            if (item.getMenuItemId() != null) {
                MenuItemEntity menuItem = menuItemsById.get(item.getMenuItemId());
                if (menuItem != null) {
                    int currentAvailable = menuItem.getAvailablePlates() != null ? menuItem.getAvailablePlates() : 0;
                    menuItem.setAvailablePlates(currentAvailable + item.getOrderedQuantity());
                }
            }
        }

        order.setStatus(StandOrderStatus.CANCELLED);
        orderRepository.save(order);

        return buildKeypadStateView(stand, config, perms);
    }

    /**
     * Forza il completamento immediato (`DELIVERED`) di un singolo ordine attivo in coda
     * dal tabellone/consegna web, segnando tutte le porzioni come cotte e consegnate.
     */
    @Transactional
    @CacheEvict(value = StandsConfig.CACHE_STAND_QUEUE_SNAPSHOTS, allEntries = true)
    @Auditable(
            action = "STAND:KEYPAD_ORDER_COMPLETE",
            description = "'Completamento rapido ordine ' + #orderId + ' su cantina ' + #standId",
            resourceId = "#standId?.toString()"
    )
    public KeypadStateView completeOrder(UUID standId, UUID orderId) {
        StandEntity stand = loadStandWithDetails(standId);
        StandKeypadConfigEntity config = ensureConfigAndDefaultSlots(stand);
        List<StandStaffRole> perms = resolveUserPermissionsForStand(stand, null, config);
        requirePermission(perms, StandStaffRole.DELIVERY, "Permesso DELIVERY o OWNER richiesto per completare un ordine.");

        StandOrderEntity order = orderRepository.findById(orderId)
                .filter(o -> standId.equals(o.getStandId()))
                .orElseThrow(() -> new StandKeypadValidationException("Ordine non trovato per questa cantina."));

        if (order.getStatus() == StandOrderStatus.CANCELLED) {
            throw new StandKeypadValidationException("Un ordine annullato non può essere segnato come consegnato.");
        }

        Instant now = Instant.now();
        for (StandOrderItemEntity item : order.getItems()) {
            item.setCookedQuantity(item.getOrderedQuantity());
            item.setDeliveredQuantity(item.getOrderedQuantity());
        }
        order.setStatus(StandOrderStatus.DELIVERED);
        if (order.getReadyAt() == null) {
            order.setReadyAt(now);
        }
        order.setDeliveredAt(now);
        orderRepository.save(order);

        return buildKeypadStateView(stand, config, perms);
    }

    private StandEntity loadStandWithDetails(UUID standId) {
        StandEntity stand = standRepository.findById(standId)
                .orElseThrow(() -> new StandNotFoundException(standId));
        stand.getOwners().size();
        stand.getMenuItems().forEach(item -> item.getKeywords().size());
        return stand;
    }

    /**
     * Crea automaticamente la configurazione `StandKeypadConfigEntity` e popola di default
     * i primi 10 slot con i piatti e le bevande del menu della cantina se non ancora configurati.
     */
    private StandKeypadConfigEntity ensureConfigAndDefaultSlots(StandEntity stand) {
        UUID standId = stand.getId();
        Instant now = Instant.now();

        StandKeypadConfigEntity config = configRepository.findById(standId).orElseGet(() -> {
            StandKeypadConfigEntity created = new StandKeypadConfigEntity(
                    standId,
                    generateDeviceToken(stand.getNumber()),
                    1,
                    now
            );
            return configRepository.save(created);
        });

        List<StandKeypadSlotEntity> existingSlots = slotRepository.findAllByStandIdOrderBySlotNumberAsc(standId);
        if (existingSlots.isEmpty()) {
            List<MenuItemEntity> menuItems = stand.getMenuItems();
            for (int slotNum = 1; slotNum <= MAX_SLOTS; slotNum++) {
                UUID defaultMenuItemId = (slotNum <= menuItems.size())
                        ? menuItems.get(slotNum - 1).getId()
                        : null;
                StandKeypadSlotEntity slotEntity = new StandKeypadSlotEntity(
                        UUID.randomUUID(),
                        standId,
                        slotNum,
                        defaultMenuItemId,
                        now
                );
                slotRepository.save(slotEntity);
            }
        } else if (existingSlots.size() < MAX_SLOTS) {
            Set<Integer> present = existingSlots.stream()
                    .map(StandKeypadSlotEntity::getSlotNumber)
                    .collect(Collectors.toSet());
            for (int slotNum = 1; slotNum <= MAX_SLOTS; slotNum++) {
                if (!present.contains(slotNum)) {
                    slotRepository.save(new StandKeypadSlotEntity(
                            UUID.randomUUID(),
                            standId,
                            slotNum,
                            null,
                            now
                    ));
                }
            }
        }

        return config;
    }

    private KeypadStateView buildKeypadStateView(
            StandEntity stand,
            StandKeypadConfigEntity config,
            List<StandStaffRole> userPermissions
    ) {
        UUID standId = stand.getId();
        List<StandKeypadSlotEntity> slotEntities = slotRepository.findAllByStandIdOrderBySlotNumberAsc(standId);
        Map<UUID, MenuItemEntity> menuItemsById = stand.getMenuItems().stream()
                .filter(it -> it.getId() != null)
                .collect(Collectors.toMap(MenuItemEntity::getId, it -> it, (a, b) -> a));

        List<StandOrderEntity> activeOrders = orderRepository.findAllByStandIdAndStatusInOrderByOrderNumberAsc(
                standId,
                ACTIVE_STATUSES
        );
        List<StandOrderEntity> recentDelivered = orderRepository.findTop10ByStandIdAndStatusOrderByOrderNumberDesc(
                standId,
                StandOrderStatus.DELIVERED
        );

        StandOrderEntity currentDeliveryOrder = activeOrders.isEmpty() ? null : activeOrders.get(0);

        int oldestKitchenOrderNumber = activeOrders.stream()
                .filter(o -> o.getItems().stream().anyMatch(it -> it.getCookedQuantity() < it.getOrderedQuantity()))
                .mapToInt(StandOrderEntity::getOrderNumber)
                .min()
                .orElse(0);

        int currentDeliveryOrderNumber = currentDeliveryOrder != null ? currentDeliveryOrder.getOrderNumber() : 0;
        int lastDeliveredOrderNumber = recentDelivered.isEmpty() ? 0 : recentDelivered.get(0).getOrderNumber();
        int lastIssuedOrderNumber = Math.max(0, config.getNextOrderNumber() - 1);

        Map<Integer, Integer> pendingToCookBySlot = new HashMap<>();
        int totalPendingKitchenPortions = 0;
        for (StandOrderEntity order : activeOrders) {
            for (StandOrderItemEntity item : order.getItems()) {
                int toCook = Math.max(0, item.getOrderedQuantity() - item.getCookedQuantity());
                if (toCook > 0) {
                    pendingToCookBySlot.merge(item.getSlotNumber(), toCook, Integer::sum);
                    totalPendingKitchenPortions += toCook;
                }
            }
        }

        Map<Integer, StandOrderItemEntity> currentDeliveryItemBySlot = new HashMap<>();
        if (currentDeliveryOrder != null) {
            for (StandOrderItemEntity item : currentDeliveryOrder.getItems()) {
                currentDeliveryItemBySlot.put(item.getSlotNumber(), item);
            }
        }

        List<SlotView> slotViews = new ArrayList<>(MAX_SLOTS);
        for (StandKeypadSlotEntity slotEntity : slotEntities) {
            int slotNum = slotEntity.getSlotNumber();
            MenuItemEntity menuItem = slotEntity.getMenuItemId() != null
                    ? menuItemsById.get(slotEntity.getMenuItemId())
                    : null;

            int availablePlates = menuItem != null && menuItem.getAvailablePlates() != null
                    ? menuItem.getAvailablePlates()
                    : 0;
            int pendingToCook = pendingToCookBySlot.getOrDefault(slotNum, 0);
            boolean kitchenLedOn = pendingToCook > 0;

            StandOrderItemEntity currentOrderSlotItem = currentDeliveryItemBySlot.get(slotNum);
            int reqCount = currentOrderSlotItem != null ? currentOrderSlotItem.getOrderedQuantity() : 0;
            int cookedCount = currentOrderSlotItem != null ? currentOrderSlotItem.getCookedQuantity() : 0;
            int deliveredCount = currentOrderSlotItem != null ? currentOrderSlotItem.getDeliveredQuantity() : 0;

            DeliveryLedState deliveryLedState;
            int remainingToDeliverForSlot = Math.max(0, reqCount - deliveredCount);
            if (remainingToDeliverForSlot == 0) {
                deliveryLedState = DeliveryLedState.OFF;
            } else if (cookedCount > deliveredCount) {
                // C'è almeno una porzione pronta da mettere sul vassoio e premere!
                deliveryLedState = DeliveryLedState.SOLID;
            } else {
                // Lo slot fa parte dell'ordine corrente #N ma la cucina lo sta ancora cuocendo
                deliveryLedState = DeliveryLedState.BLINKING;
            }

            slotViews.add(new SlotView(
                    slotNum,
                    menuItem != null ? menuItem.getId() : null,
                    menuItem != null ? menuItem.getName() : null,
                    menuItem != null ? menuItem.getKind() : null,
                    availablePlates,
                    pendingToCook,
                    reqCount,
                    cookedCount,
                    deliveredCount,
                    kitchenLedOn,
                    deliveryLedState
            ));
        }

        boolean isOwner = userPermissions.contains(StandStaffRole.OWNER);
        List<StaffPermissionView> staffPermissionViews = isOwner
                ? permissionRepository.findAllByStandIdOrderByUsernameAscPermissionAsc(standId).stream()
                .map(p -> new StaffPermissionView(
                        p.getId(),
                        p.getStandId(),
                        p.getUsername(),
                        p.getPermission(),
                        p.getGrantedBy(),
                        p.getCreatedAt()
                ))
                .toList()
                : List.of();

        return new KeypadStateView(
                standId,
                stand.getNumber(),
                stand.getName(),
                isOwner ? config.getDeviceToken() : null,
                config.getNextOrderNumber(),
                lastIssuedOrderNumber,
                oldestKitchenOrderNumber,
                currentDeliveryOrderNumber,
                lastDeliveredOrderNumber,
                activeOrders.size(),
                totalPendingKitchenPortions,
                config.getLastCashierSeenAt(),
                config.getLastKitchenSeenAt(),
                config.getLastDeliverySeenAt(),
                userPermissions,
                slotViews,
                activeOrders.stream().map(this::toOrderView).toList(),
                recentDelivered.stream().map(this::toOrderView).toList(),
                staffPermissionViews
        );
    }

    private OrderView toOrderView(StandOrderEntity entity) {
        List<OrderItemView> itemViews = entity.getItems().stream()
                .map(it -> new OrderItemView(
                        it.getId(),
                        it.getSlotNumber(),
                        it.getMenuItemId(),
                        it.getMenuItemName(),
                        it.getKind(),
                        it.getOrderedQuantity(),
                        it.getCookedQuantity(),
                        it.getDeliveredQuantity()
                ))
                .toList();
        return new OrderView(
                entity.getId(),
                entity.getStandId(),
                entity.getOrderNumber(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getReadyAt(),
                entity.getDeliveredAt(),
                itemViews
        );
    }

    private List<StandStaffRole> resolveUserPermissionsForStand(
            StandEntity stand,
            String deviceToken
    ) {
        StandKeypadConfigEntity config = configRepository != null
                ? configRepository.findById(stand.getId()).orElse(null)
                : null;
        return resolveUserPermissionsForStand(stand, deviceToken, config);
    }

    /**
     * Risolve i ruoli operativi dell'utente corrente (o dell'ESP32 tramite `X-Device-Token`)
     * per una specifica cantina:
     * 1. Se `X-Device-Token` corrisponde al `deviceToken` della cantina -> abilita `CASHIER`, `KITCHEN`, `DELIVERY`, `VIEWER`.
     * 2. Se l'utente ha ruolo globale Keycloak `ADMIN` o `MEZZ` -> abilita tutti i ruoli incluso `OWNER`.
     * 3. Se l'utente è proprietario della cantina (`StandOwnerEntity` per username/email/nome) -> abilita tutti i ruoli incluso `OWNER`.
     * 4. Se l'utente è registrato in `stand_staff_permissions` (es. `maria-chiara` -> `CASHIER`,
     * `scricci` -> `DELIVERY`, `lisa-la` -> `VIEWER`) -> restituisce i ruoli assegnati (aggiungendo sempre `VIEWER` per poter leggere lo stato della cantina).
     */
    private List<StandStaffRole> resolveUserPermissionsForStand(
            StandEntity stand,
            String deviceToken,
            StandKeypadConfigEntity config
    ) {
        if (deviceToken != null && !deviceToken.isBlank() && config != null
                && config.getDeviceToken() != null
                && config.getDeviceToken().equals(deviceToken.trim())) {
            return List.of(
                    StandStaffRole.CASHIER,
                    StandStaffRole.KITCHEN,
                    StandStaffRole.DELIVERY,
                    StandStaffRole.VIEWER
            );
        }

        AuditUser currentUser = resolveAuditUser();
        Set<String> jwtRoles = extractJwtRolesFromRequest();

        if (jwtRoles.contains("ADMIN") || jwtRoles.contains("ROLE_ADMIN")
                || jwtRoles.contains("MEZZ") || jwtRoles.contains("ROLE_MEZZ")) {
            return List.of(
                    StandStaffRole.OWNER,
                    StandStaffRole.CASHIER,
                    StandStaffRole.KITCHEN,
                    StandStaffRole.DELIVERY,
                    StandStaffRole.VIEWER
            );
        }

        String username = currentUser != null && currentUser.username() != null
                ? currentUser.username().trim().toLowerCase(Locale.ROOT)
                : "";
        if (username.isEmpty() || "anonymous".equals(username)) {
            return List.of();
        }

        if (isStandOwnerMatch(stand, username)) {
            return List.of(
                    StandStaffRole.OWNER,
                    StandStaffRole.CASHIER,
                    StandStaffRole.KITCHEN,
                    StandStaffRole.DELIVERY,
                    StandStaffRole.VIEWER
            );
        }

        EnumSet<StandStaffRole> roles = EnumSet.noneOf(StandStaffRole.class);
        if (permissionRepository != null && stand.getId() != null) {
            List<StandStaffPermissionEntity> assigned = permissionRepository.findAllByStandIdOrderByUsernameAscPermissionAsc(stand.getId());
            for (StandStaffPermissionEntity entity : assigned) {
                if (entity.getUsername() != null && entity.getUsername().trim().equalsIgnoreCase(username)) {
                    roles.add(entity.getPermission());
                }
            }
        }

        if (roles.contains(StandStaffRole.OWNER)) {
            return List.of(
                    StandStaffRole.OWNER,
                    StandStaffRole.CASHIER,
                    StandStaffRole.KITCHEN,
                    StandStaffRole.DELIVERY,
                    StandStaffRole.VIEWER
            );
        }

        if (!roles.isEmpty()) {
            // Chiunque abbia un ruolo operativo sulla cantina (CASHIER, KITCHEN, DELIVERY) ha anche lettura (VIEWER)
            roles.add(StandStaffRole.VIEWER);
        }

        return new ArrayList<>(roles);
    }

    private boolean isStandOwnerMatch(StandEntity stand, String usernameLower) {
        if (stand.getOwners() == null) {
            return false;
        }
        for (StandOwnerEntity owner : stand.getOwners()) {
            String first = owner.getFirstName() != null ? owner.getFirstName().trim().toLowerCase(Locale.ROOT) : "";
            String last = owner.getLastName() != null ? owner.getLastName().trim().toLowerCase(Locale.ROOT) : "";
            String email = owner.getEmail() != null ? owner.getEmail().trim().toLowerCase(Locale.ROOT) : "";
            String emailPrefix = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            String hyphenFullName = (first + "-" + last).replaceAll("\\s+", "-");
            String spaceFullName = (first + " " + last).trim();

            if (usernameLower.equals(first)
                    || usernameLower.equals(last)
                    || usernameLower.equals(email)
                    || usernameLower.equals(emailPrefix)
                    || usernameLower.equals(hyphenFullName)
                    || usernameLower.equals(spaceFullName)) {
                return true;
            }
        }
        return false;
    }

    private void requirePermission(List<StandStaffRole> userPerms, StandStaffRole required, String errorMessage) {
        if (userPerms.contains(StandStaffRole.OWNER) || userPerms.contains(required)) {
            return;
        }
        throw new StandAccessDeniedException(errorMessage);
    }

    private AuditUser resolveAuditUser() {
        return auditUserResolver != null ? auditUserResolver.resolveUser() : AuditUser.ANONYMOUS;
    }

    public record StandQueueSnapshot(
            int activeOrdersCount,
            int currentServingOrderNumber,
            int estimatedWaitMinutes
    ) {
        public static final StandQueueSnapshot EMPTY = new StandQueueSnapshot(0, 0, 0);
    }

    public record AccessibleStandView(
            UUID standId,
            int standNumber,
            String standName,
            List<StandStaffRole> permissions,
            int activeOrdersCount,
            int currentServingOrderNumber
    ) {
    }

    public record SlotView(
            int slotNumber,
            UUID menuItemId,
            String menuItemName,
            MenuKind kind,
            int availablePlates,
            int pendingToCookCount,
            int currentDeliveryOrderRequiredCount,
            int currentDeliveryOrderCookedCount,
            int currentDeliveryOrderDeliveredCount,
            boolean kitchenLedOn,
            DeliveryLedState deliveryLedState
    ) {
    }

    public record OrderItemView(
            UUID id,
            int slotNumber,
            UUID menuItemId,
            String menuItemName,
            MenuKind kind,
            int orderedQuantity,
            int cookedQuantity,
            int deliveredQuantity
    ) {
    }

    public record OrderView(
            UUID id,
            UUID standId,
            int orderNumber,
            StandOrderStatus status,
            String createdBy,
            Instant createdAt,
            Instant readyAt,
            Instant deliveredAt,
            List<OrderItemView> items
    ) {
    }

    public record StaffPermissionView(
            UUID id,
            UUID standId,
            String username,
            StandStaffRole permission,
            String grantedBy,
            Instant createdAt
    ) {
    }

    public record KeypadStateView(
            UUID standId,
            int standNumber,
            String standName,
            String deviceToken,
            int nextOrderNumber,
            int lastIssuedOrderNumber,
            int oldestKitchenOrderNumber,
            int currentDeliveryOrderNumber,
            int lastDeliveredOrderNumber,
            int activeOrdersCount,
            int totalPendingKitchenPortions,
            Instant lastCashierSeenAt,
            Instant lastKitchenSeenAt,
            Instant lastDeliverySeenAt,
            List<StandStaffRole> userPermissions,
            List<SlotView> slots,
            List<OrderView> activeOrders,
            List<OrderView> recentDeliveredOrders,
            List<StaffPermissionView> staffPermissions
    ) {
    }

    public record SlotAssignmentInput(int slotNumber, UUID menuItemId) {
    }

    public record OrderItemInput(int slotNumber, int quantity) {
    }
}
