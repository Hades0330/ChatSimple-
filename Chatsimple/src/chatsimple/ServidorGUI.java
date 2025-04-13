import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;

public class ServidorGUI {
    private static final int PORT = 12345;
    private static Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());
    private static Map<Socket, String> usuariosConectados = Collections.synchronizedMap(new HashMap<>());
    private static ServidorGUI instancia;

    private JFrame frame;
    private JTextArea logArea;

    public static synchronized ServidorGUI getInstancia() {
        if (instancia == null) {
            instancia = new ServidorGUI();
        }
        return instancia;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> ServidorGUI.getInstancia().start());
    }

    public void start() {
        frame = new JFrame("Servidor de Chat");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Panel para enviar mensaje desde el servidor
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JTextField serverMessageField = new JTextField();
        JButton enviarButton = new JButton("Enviar a todos");

        enviarButton.addActionListener(e -> {
            String texto = serverMessageField.getText().trim();
            if (!texto.isEmpty()) {
                Mensaje mensaje = MensajeFactory.crearMensaje("alerta", "Servidor: " + texto);
                enviarMensajeServidor(mensaje);
                serverMessageField.setText("");
            }
        });

        bottomPanel.add(serverMessageField, BorderLayout.CENTER);
        bottomPanel.add(enviarButton, BorderLayout.EAST);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
        new Thread(this::startServer).start();
    }

    private void startServer() {
        log("Servidor iniciado en el puerto " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            log("Error en el servidor: " + e.getMessage());
        }
    }

    private void log(String mensaje) {
        SwingUtilities.invokeLater(() -> logArea.append(mensaje + "\n"));
    }

    private void enviarMensajeServidor(Mensaje mensaje) {
        log("Enviado por servidor: " + mensaje.formatear());
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println(mensaje.formatear());
            }
        }
    }

    private class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                clientWriters.add(out);

                // Esperar login
                String entrada = in.readLine();
                if (entrada != null && entrada.startsWith("/login ")) {
                    username = entrada.substring(7).trim();
                    usuariosConectados.put(socket, username);
                    log("Usuario conectado: " + username);
                    broadcast(MensajeFactory.crearMensaje("notificacion", username + " se ha conectado."));
                    enviarListaUsuarios();
                }

                String mensaje;
                while ((mensaje = in.readLine()) != null) {
                    Mensaje msg = MensajeFactory.crearMensaje("texto", mensaje);
                    log("Mensaje recibido: " + msg.formatear());
                    broadcast(msg);
                }

            } catch (IOException e) {
                log("Error con cliente: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                }

                clientWriters.remove(out);
                if (username != null) {
                    log("Usuario desconectado: " + username);
                    usuariosConectados.remove(socket);
                    broadcast(MensajeFactory.crearMensaje("notificacion", username + " se ha desconectado."));
                    enviarListaUsuarios();
                }
            }
        }

        private void broadcast(Mensaje mensaje) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(mensaje.formatear());
                }
            }
        }

        private void enviarListaUsuarios() {
            StringBuilder lista = new StringBuilder("/usuarios ");
            synchronized (usuariosConectados) {
                for (String user : usuariosConectados.values()) {
                    lista.append(user).append(",");
                }
            }
            String listaFinal = lista.toString();
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(listaFinal);
                }
            }
        }
    }
}

// --- Clases del Factory Method ---
abstract class Mensaje {
    protected String contenido;
    public Mensaje(String contenido) {
        this.contenido = contenido;
    }
    public abstract String formatear();
}

class MensajeTexto extends Mensaje {
    public MensajeTexto(String contenido) {
        super(contenido);
    }
    public String formatear() {
        return "[TEXTO] " + contenido;
    }
}

class MensajeAlerta extends Mensaje {
    public MensajeAlerta(String contenido) {
        super(contenido);
    }
    public String formatear() {
        return "[ALERTA] " + contenido;
    }
}

class MensajeNotificacion extends Mensaje {
    public MensajeNotificacion(String contenido) {
        super(contenido);
    }
    public String formatear() {
        return "[NOTIFICACIÓN] " + contenido;
    }
}

class MensajeFactory {
    public static Mensaje crearMensaje(String tipo, String contenido) {
        return switch (tipo.toLowerCase()) {
            case "texto" -> new MensajeTexto(contenido);
            case "alerta" -> new MensajeAlerta(contenido);
            case "notificacion" -> new MensajeNotificacion(contenido);
            default -> new MensajeTexto(contenido);
        };
    }
}