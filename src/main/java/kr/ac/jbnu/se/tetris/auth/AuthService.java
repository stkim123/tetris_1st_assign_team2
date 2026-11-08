package kr.ac.jbnu.se.tetris.auth;

import java.io.IOException;

/** Common desktop account operations. Implementations keep credentials in process memory only. */
public interface AuthService {
    SignUpResult signUp(String email, String password) throws AuthException, IOException;
    SignUpResult signUpUsername(String username, String password) throws AuthException, IOException;
    AuthSession signIn(String email, String password) throws AuthException, IOException;
    AuthSession signInUsername(String username, String password) throws AuthException, IOException;
    AuthSession refresh() throws AuthException, IOException;
    void signOut() throws AuthException, IOException;
    void recoverPassword(String email) throws AuthException, IOException;
    AuthSession completePasswordRecovery(String email, String code, String newPassword) throws AuthException, IOException;
    AuthSession changePassword(String newPassword) throws AuthException, IOException;
    AuthSession getSession();
    void discardSessionIf(AuthSession discarded);
}
