import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Exercicio 2 - Cliente da sala de chat (porta 9002).
 *
 * Abra varias instancias deste cliente para ver o servidor intermediando a
 * conversa entre todos.
 *
 * Execucao:
 *   javac ClienteChat.java && java ClienteChat [host] [porta]
 */
public class ClienteChat {

    private static final String HOST_PADRAO = "localhost";
    private static final int PORTA_PADRAO = 9002;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : HOST_PADRAO;
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : PORTA_PADRAO;

        try (Socket socket = new Socket(host, porta);
             BufferedReader daRede = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), "UTF-8"));
             PrintWriter paraRede = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
             BufferedReader doTeclado = new BufferedReader(
                     new InputStreamReader(System.in))) {

            System.out.println("[CLIENTE] Conectado a sala em " + host + ":" + porta + "\n");

            // Thread que imprime tudo que a sala manda (mensagens dos outros
            // participantes e avisos do servidor).
            Thread recebedor = new Thread(() -> {
                try {
                    String recebida;
                    while ((recebida = daRede.readLine()) != null) {
                        System.out.println(recebida);
                    }
                    System.out.println("\n[CLIENTE] O servidor fechou a conexao.");
                } catch (IOException e) {
                    System.out.println("\n[CLIENTE] Conexao encerrada.");
                }
            });
            recebedor.setDaemon(true);
            recebedor.start();

            // Thread principal: envia o apelido e depois as mensagens.
            String digitada;
            while ((digitada = doTeclado.readLine()) != null) {
                paraRede.println(digitada);
                if (digitada.equalsIgnoreCase("sair")) {
                    System.out.println("[CLIENTE] Saindo da sala.");
                    break;
                }
            }
        } catch (java.net.ConnectException e) {
            System.err.println("[CLIENTE] Nao foi possivel conectar. O servidor esta rodando?");
        } catch (IOException e) {
            System.err.println("[CLIENTE] Erro: " + e.getMessage());
        }
    }
}
