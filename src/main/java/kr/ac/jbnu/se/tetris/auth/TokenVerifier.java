package kr.ac.jbnu.se.tetris.auth;

import java.io.IOException;

public interface TokenVerifier {
    AuthIdentity verify(String accessToken) throws AuthException, IOException;
}
