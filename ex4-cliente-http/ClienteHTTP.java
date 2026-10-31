import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Exercicio 4 - Cliente que solicita a pagina inicial de http://www.aied.com.br
 * usando socket TCP puro, sem nenhuma biblioteca HTTP (sem HttpClient, sem
 * URLConnection).
 *
 * O objetivo e mostrar que o HTTP e apenas texto trafegando sobre o TCP: abrimos
 * o socket na porta 80 e escrevemos a requisicao na mao, linha por linha, no
 * formato definido pela RFC 7230:
 *
 *   GET / HTTP/1.1\r\n
 *   Host: www.aied.com.br\r\n
 *   Connection: close\r\n
 *   \r\n                      <- linha vazia marca o fim dos cabecalhos
 *
 * Detalhes que o exercicio evidencia:
 *   - O terminador de linha do HTTP e CRLF ("\r\n"), nao "\n".
 *   - O cabecalho Host e obrigatorio no HTTP/1.1, pois um mesmo IP pode
 *     hospedar varios sites (virtual hosting).
 *   - "Connection: close" faz o servidor fechar a conexao ao terminar, assim
 *     sabemos que a resposta acabou quando read() retorna -1.
 *   - Este site responde 301 redirecionando para HTTPS, entao o cliente segue
 *     o cabecalho Location. Para o endereco https usamos SSLSocket, que e um
 *     socket TCP comum com uma camada TLS por cima: a requisicao continua
 *     sendo escrita a mao, exatamente igual.
 *
 * Execucao:
 *   javac ClienteHTTP.java && java ClienteHTTP
 *   java ClienteHTTP http://www.aied.com.br      (outra URL, opcional)
 */
public class ClienteHTTP {

    private static final String URL_PADRAO = "http://www.aied.com.br/";
    private static final String CRLF = "\r\n";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECIONAMENTOS = 5;

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : URL_PADRAO;

        try {
            String corpoFinal = null;

            // Segue a cadeia de redirecionamentos (301/302) ate chegar na pagina.
            for (int salto = 0; salto <= MAX_REDIRECIONAMENTOS; salto++) {
                Endereco destino = Endereco.analisar(url);
                System.out.println("=========================================");
                System.out.println("[CLIENTE] Requisicao " + (salto + 1) + ": " + url);
                System.out.println("=========================================\n");

                byte[] resposta = requisitar(destino);
                if (resposta == null) {
                    return;
                }

                // A linha em branco (CRLF CRLF) separa cabecalhos do corpo.
                String texto = new String(resposta, StandardCharsets.UTF_8);
                int separador = texto.indexOf(CRLF + CRLF);
                String cabecalhos = separador >= 0 ? texto.substring(0, separador) : texto;
                String corpo = separador >= 0 ? texto.substring(separador + 4) : "";

                System.out.println("----- CABECALHOS DA RESPOSTA -----");
                System.out.println(cabecalhos);
                System.out.println("----------------------------------\n");

                String statusLine = cabecalhos.lines().findFirst().orElse("");
                String local = cabecalho(cabecalhos, "location");

                // 301/302/303/307/308 com Location: repete a requisicao no novo endereco.
                if (ehRedirecionamento(statusLine) && local != null) {
                    System.out.println("[CLIENTE] Redirecionado para -> " + local + "\n");
                    url = local.startsWith("http") ? local : destino.base() + local;
                    continue;
                }

                // Avisa quando o servidor recusou a requisicao. No caso do
                // www.aied.com.br o endereco https responde 403 ao nosso
                // User-Agent: e a politica do CDN que hospeda o site, nao um
                // erro do cliente - a requisicao HTTP foi montada e entendida
                // corretamente, como mostra a linha de status recebida.
                if (!statusLine.contains(" 200 ")) {
                    System.out.println("[CLIENTE] Atencao: o servidor respondeu '"
                            + statusLine.trim() + "' em vez de 200 OK.\n");
                }

                corpoFinal = corpo;
                break;
            }

            if (corpoFinal == null) {
                System.err.println("[CLIENTE] Excesso de redirecionamentos.");
                return;
            }

            // Mostra so o inicio do HTML no terminal; o resto vai para o arquivo.
            System.out.println("----- INICIO DO CORPO (HTML) -----");
            System.out.println(corpoFinal.length() > 1500
                    ? corpoFinal.substring(0, 1500) + "\n[...]"
                    : corpoFinal);
            System.out.println("----------------------------------\n");

            Path arquivo = Paths.get("pagina-recebida.html");
            Files.write(arquivo, corpoFinal.getBytes(StandardCharsets.UTF_8));
            System.out.println("[CLIENTE] Pagina inicial salva em " + arquivo.toAbsolutePath()
                    + " (" + corpoFinal.length() + " caracteres)");
        } catch (IllegalArgumentException e) {
            System.err.println("[CLIENTE] URL invalida: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[CLIENTE] Erro: " + e.getMessage());
        }
    }

    /**
     * Abre o socket, escreve a requisicao GET a mao e le a resposta inteira.
     * Retorna os bytes crus (cabecalhos + corpo) ou null em caso de falha.
     */
    private static byte[] requisitar(Endereco destino) throws IOException {
        try {
            InetAddress ip = InetAddress.getByName(destino.host);
            System.out.println("[CLIENTE] DNS: " + destino.host + " -> " + ip.getHostAddress());
        } catch (IOException e) {
            System.err.println("[CLIENTE] Falha ao resolver '" + destino.host + "'");
            return null;
        }

        // Para https, o SSLSocketFactory devolve um socket TCP com TLS por cima.
        // Para http, e o socket TCP cru da porta 80.
        try (Socket socket = destino.tls
                ? SSLSocketFactory.getDefault().createSocket()
                : new Socket()) {

            socket.connect(new InetSocketAddress(destino.host, destino.porta), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            if (socket instanceof SSLSocket tlsSocket) {
                tlsSocket.startHandshake();
                System.out.println("[CLIENTE] TLS estabelecido ("
                        + tlsSocket.getSession().getProtocol() + ")");
            }
            System.out.println("[CLIENTE] Conectado em " + destino.host + ":" + destino.porta + "\n");

            // ---------- Monta e envia a requisicao HTTP ----------
            String requisicao =
                    "GET " + destino.caminho + " HTTP/1.1" + CRLF
                  + "Host: " + destino.host + CRLF
                  + "User-Agent: ClienteTCP-Fatec/1.0" + CRLF
                  + "Accept: text/html" + CRLF
                  + "Connection: close" + CRLF
                  + CRLF;

            System.out.println("----- REQUISICAO ENVIADA -----");
            System.out.print(requisicao);
            System.out.println("------------------------------\n");

            OutputStream saida = socket.getOutputStream();
            saida.write(requisicao.getBytes(StandardCharsets.US_ASCII));
            saida.flush();

            // ---------- Le a resposta completa ----------
            // Lemos bytes (e nao texto) porque o corpo pode vir em qualquer
            // codificacao. read() retorna -1 quando o servidor fecha a conexao,
            // o que sinaliza o fim da resposta graças ao "Connection: close".
            ByteArrayOutputStream bruta = new ByteArrayOutputStream();
            try (InputStream entrada = new BufferedInputStream(socket.getInputStream())) {
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = entrada.read(buffer)) != -1) {
                    bruta.write(buffer, 0, lidos);
                }
            }

            byte[] resposta = bruta.toByteArray();
            System.out.println("[CLIENTE] Resposta recebida: " + resposta.length + " bytes\n");
            return resposta;
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("[CLIENTE] Tempo esgotado ao falar com o servidor.");
            return null;
        }
    }

    /** True para os codigos de status que indicam redirecionamento. */
    private static boolean ehRedirecionamento(String statusLine) {
        for (String codigo : new String[] {" 301 ", " 302 ", " 303 ", " 307 ", " 308 "}) {
            if (statusLine.contains(codigo)) {
                return true;
            }
        }
        return false;
    }

    /** Procura um cabecalho pelo nome, ignorando maiusculas/minusculas. */
    private static String cabecalho(String cabecalhos, String nome) {
        return cabecalhos.lines()
                .filter(linha -> linha.toLowerCase().startsWith(nome.toLowerCase() + ":"))
                .map(linha -> linha.substring(linha.indexOf(':') + 1).trim())
                .findFirst()
                .orElse(null);
    }

    /** Quebra uma URL em host, porta, caminho e se usa TLS - tudo na mao. */
    private record Endereco(String host, int porta, String caminho, boolean tls) {

        static Endereco analisar(String url) {
            boolean tls = url.startsWith("https://");
            String resto = url.replaceFirst("^https?://", "");
            if (resto.isEmpty()) {
                throw new IllegalArgumentException(url);
            }

            int barra = resto.indexOf('/');
            String autoridade = barra >= 0 ? resto.substring(0, barra) : resto;
            String caminho = barra >= 0 ? resto.substring(barra) : "/";

            // Porta explicita na URL (ex.: exemplo.com:8080) ou o padrao do esquema.
            int porta = tls ? 443 : 80;
            int doisPontos = autoridade.indexOf(':');
            if (doisPontos >= 0) {
                porta = Integer.parseInt(autoridade.substring(doisPontos + 1));
                autoridade = autoridade.substring(0, doisPontos);
            }

            return new Endereco(autoridade, porta, caminho, tls);
        }

        /** Raiz do site, usada para resolver um Location relativo. */
        String base() {
            return (tls ? "https://" : "http://") + host;
        }
    }
}
