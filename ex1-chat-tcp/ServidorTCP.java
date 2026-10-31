import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Exercicio 1 - Servidor TCP que troca mensagens de texto na porta 9001.
 *
 * O servidor abre a porta, espera a conexao de um cliente e entao a conversa
 * passa a ser bidirecional: uma thread fica dedicada a ler o que chega pela
 * rede, enquanto a thread principal le o que o usuario digita no teclado e
 * envia para o cliente.
 *
 * Execucao:
 *   javac ServidorTCP.java && java ServidorTCP
 */
public class ServidorTCP {

    private static final int PORTA = 9001;

    public static void main(String[] args) {
        // try-with-resources garante que os sockets sejam fechados no final.
        try (ServerSocket servidor = new ServerSocket(PORTA)) {
            System.out.println("[SERVIDOR] Escutando na porta " + PORTA + "...");
            System.out.println("[SERVIDOR] Aguardando conexao de um cliente.");

            // accept() bloqueia a execucao ate que algum cliente se conecte.
            try (Socket cliente = servidor.accept();
                 BufferedReader daRede = new BufferedReader(
                         new InputStreamReader(cliente.getInputStream(), "UTF-8"));
                 PrintWriter paraRede = new PrintWriter(
                         new java.io.OutputStreamWriter(cliente.getOutputStream(), "UTF-8"), true);
                 BufferedReader doTeclado = new BufferedReader(
                         new InputStreamReader(System.in))) {

                System.out.println("[SERVIDOR] Cliente conectado: "
                        + cliente.getInetAddress().getHostAddress()
                        + ":" + cliente.getPort());
                System.out.println("[SERVIDOR] Digite suas mensagens (ou 'sair' para encerrar).\n");

                // Thread dedicada a receber as mensagens do cliente. Sem ela, o
                // programa ficaria travado esperando o teclado e nao mostraria
                // nada do que chegasse pela rede nesse meio tempo.
                Thread recebedor = new Thread(() -> {
                    try {
                        String recebida;
                        while ((recebida = daRede.readLine()) != null) {
                            if (recebida.equalsIgnoreCase("sair")) {
                                System.out.println("\n[SERVIDOR] O cliente encerrou a conversa.");
                                break;
                            }
                            System.out.println("[CLIENTE] " + recebida);
                        }
                    } catch (IOException e) {
                        // Acontece quando a conexao e fechada do outro lado.
                        System.out.println("\n[SERVIDOR] Conexao encerrada.");
                    }
                });
                recebedor.setDaemon(true);
                recebedor.start();

                // Thread principal: le o teclado e envia para o cliente.
                String digitada;
                while ((digitada = doTeclado.readLine()) != null) {
                    paraRede.println(digitada);
                    if (digitada.equalsIgnoreCase("sair")) {
                        System.out.println("[SERVIDOR] Encerrando a conversa.");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro: " + e.getMessage());
        }
    }
}
