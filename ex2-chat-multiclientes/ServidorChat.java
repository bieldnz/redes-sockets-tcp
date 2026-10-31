import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Exercicio 2 - Servidor que intermedia a conversa de texto entre 2 ou mais
 * clientes na porta 9002.
 *
 * O servidor nao conversa: ele apenas repassa (faz broadcast) toda mensagem
 * recebida de um cliente para todos os outros. Cada cliente aceito ganha sua
 * propria thread, para que varios possam falar ao mesmo tempo.
 *
 * Execucao:
 *   javac ServidorChat.java && java ServidorChat
 */
public class ServidorChat {

    private static final int PORTA = 9002;

    /**
     * Lista dos clientes conectados. Precisa ser sincronizada porque varias
     * threads (uma por cliente) leem e escrevem nela ao mesmo tempo.
     */
    private static final Set<Atendente> CLIENTES =
            Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(PORTA)) {
            System.out.println("[SERVIDOR] Sala de chat aberta na porta " + PORTA + ".");
            System.out.println("[SERVIDOR] Aguardando clientes...\n");

            // Laco infinito: o servidor nunca para de aceitar novos clientes.
            while (true) {
                Socket socket = servidor.accept();
                Atendente atendente = new Atendente(socket);
                CLIENTES.add(atendente);
                atendente.start();
            }
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro: " + e.getMessage());
        }
    }

    /**
     * Envia uma mensagem para todos os clientes conectados, menos para o autor.
     */
    private static void transmitir(String mensagem, Atendente autor) {
        System.out.println(mensagem);
        // O synchronized evita que a lista mude enquanto estamos percorrendo.
        synchronized (CLIENTES) {
            for (Atendente destino : CLIENTES) {
                if (destino != autor) {
                    destino.enviar(mensagem);
                }
            }
        }
    }

    /**
     * Uma thread por cliente conectado: le as mensagens daquele cliente e as
     * repassa para a sala.
     */
    private static class Atendente extends Thread {

        private final Socket socket;
        private PrintWriter paraRede;
        private String apelido;

        Atendente(Socket socket) {
            this.socket = socket;
        }

        /** Escreve uma linha no socket deste cliente. */
        void enviar(String mensagem) {
            if (paraRede != null) {
                paraRede.println(mensagem);
            }
        }

        @Override
        public void run() {
            try (BufferedReader daRede = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

                paraRede = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // A primeira linha enviada pelo cliente e o apelido dele.
                paraRede.println("Bem-vindo! Digite seu apelido:");
                apelido = daRede.readLine();
                if (apelido == null || apelido.isBlank()) {
                    apelido = "Anonimo-" + socket.getPort();
                }

                paraRede.println("Voce entrou como '" + apelido
                        + "'. Digite 'sair' para deixar a sala.");
                transmitir("*** " + apelido + " entrou na sala ("
                        + CLIENTES.size() + " online) ***", this);

                // A partir daqui, tudo que o cliente mandar e repassado.
                String mensagem;
                while ((mensagem = daRede.readLine()) != null) {
                    if (mensagem.equalsIgnoreCase("sair")) {
                        break;
                    }
                    transmitir("[" + apelido + "] " + mensagem, this);
                }
            } catch (IOException e) {
                System.out.println("[SERVIDOR] Conexao perdida com "
                        + (apelido != null ? apelido : "cliente desconhecido"));
            } finally {
                // Independente do motivo da saida, o cliente sai da lista e o
                // socket e fechado.
                CLIENTES.remove(this);
                if (apelido != null) {
                    transmitir("*** " + apelido + " saiu da sala ("
                            + CLIENTES.size() + " online) ***", this);
                }
                try {
                    socket.close();
                } catch (IOException ignorado) {
                    // Nada a fazer: ja estamos encerrando este cliente.
                }
            }
        }
    }
}
