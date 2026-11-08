package kr.ac.jbnu.se.tetris.network;

import java.net.URI;

/** 실제 네트워크 클라이언트의 서버 접속 대상 */
public final class ConnectionOptions {
    private final String host;
    private final int port;
    private final URI webSocketUri;
    private final String accessToken;

    public ConnectionOptions(URI webSocketUri, String accessToken) {
        if (webSocketUri == null || accessToken == null || accessToken.trim().isEmpty()
                || accessToken.length() > 8192 || webSocketUri.getHost() == null
                || !("ws".equalsIgnoreCase(webSocketUri.getScheme())
                        || "wss".equalsIgnoreCase(webSocketUri.getScheme()))
                || webSocketUri.getUserInfo() != null || webSocketUri.getQuery() != null
                || webSocketUri.getFragment() != null || !"/ws".equals(webSocketUri.getPath())
                || ("ws".equalsIgnoreCase(webSocketUri.getScheme())
                        && !isLoopback(webSocketUri.getHost()))) {
            throw new IllegalArgumentException("Invalid WebSocket connection options");
        }
        for (int index = 0; index < accessToken.length(); index++) {
            char character = accessToken.charAt(index);
            if (character < 33 || character > 126) {
                throw new IllegalArgumentException("Invalid access token header");
            }
        }
        int remotePort = webSocketUri.getPort();
        if (remotePort == -1) remotePort = "wss".equalsIgnoreCase(webSocketUri.getScheme()) ? 443 : 80;
        if (remotePort < 1 || remotePort > 65535) throw new IllegalArgumentException("Invalid WebSocket port");
        this.host = webSocketUri.getHost();
        this.port = remotePort;
        this.webSocketUri = webSocketUri;
        this.accessToken = accessToken.trim();
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public URI getWebSocketUri() { return webSocketUri; }
    public String getAccessToken() { return accessToken; }
}
