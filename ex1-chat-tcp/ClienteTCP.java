import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Exercicio 1 - Cliente TCP que troca mensagens de texto na porta 9001.
 *
 * Conecta no servidor e, assim como ele, usa uma thread para receber e a
 * thread principal para enviar o que o usuario digita.
 *
 * Execucao:
 *   javac ClienteTCP.java && java ClienteTCP [host] [porta]
 */
public class ClienteTCP {

    private static final String HOST_PADRAO = "localhost";
    private static final int PORTA_PADRAO = 9001;

    public static void main(String[] args) {
        // Permite passar host e porta pela linha de comando para testar entre
        // maquinas diferentes na rede.
        String host = args.length > 0 ? args[0] : HOST_PADRAO;
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : PORTA_PADRAO;

        System.out.println("[CLIENTE] Conectando em " + host + ":" + porta + "...");

        try (Socket socket = new Socket(host, porta);
             BufferedReader daRede = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), "UTF-8"));
             PrintWriter paraRede = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
             BufferedReader doTeclado = new BufferedReader(
                     new InputStreamReader(System.in))) {

            System.out.println("[CLIENTE] Conectado ao servidor.");
            System.out.println("[CLIENTE] Digite suas mensagens (ou 'sair' para encerrar).\n");

            // Thread que fica escutando o servidor.
            Thread recebedor = new Thread(() -> {
                try {
                    String recebida;
                    while ((recebida = daRede.readLine()) != null) {
                        if (recebida.equalsIgnoreCase("sair")) {
                            System.out.println("\n[CLIENTE] O servidor encerrou a conversa.");
                            break;
                        }
                        System.out.println("[SERVIDOR] " + recebida);
                    }
                } catch (IOException e) {
                    System.out.println("\n[CLIENTE] Conexao encerrada.");
                }
            });
            recebedor.setDaemon(true);
            recebedor.start();

            String digitada;
            while ((digitada = doTeclado.readLine()) != null) {
                paraRede.println(digitada);
                if (digitada.equalsIgnoreCase("sair")) {
                    System.out.println("[CLIENTE] Encerrando a conversa.");
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
