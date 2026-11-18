package cl.thereyesrecords.disquera.service;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${google.client.id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleIdToken.Payload verificarToken(String idTokenString)
            throws Exception {

        GoogleIdToken idToken = verifier.verify(idTokenString);

        if (idToken == null) {
            throw new IllegalArgumentException(
                "Token de Google invalido o expirado");
        }
        return idToken.getPayload();
    }
}