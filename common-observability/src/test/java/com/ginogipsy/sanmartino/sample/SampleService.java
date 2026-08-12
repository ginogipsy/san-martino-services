package com.ginogipsy.sanmartino.sample;

import com.ginogipsy.sanmartino.observability.Masked;
import org.springframework.stereotype.Service;

/**
 * Fixture per i test dell'aspect. Sta fuori dal package {@code observability}
 * perché il pointcut lo esclude di proposito (l'aspect non deve intercettare
 * se stesso), quindi un target dichiarato lì non verrebbe mai avvolto.
 */
@Service
public class SampleService {

    public String greet(String name) {
        return "ciao " + name;
    }

    public String login(String username, String password) {
        return "session-for-" + username;
    }

    public String rotate(@Masked String credentials) {
        return "rotated";
    }

    public void expected() {
        throw new SampleNotFoundException("id 42");
    }

    public void unexpected() {
        throw new IllegalStateException("boom");
    }

    public void nothing() {
        // metodo void: il log di uscita non deve mostrare alcun valore di ritorno
    }
}
