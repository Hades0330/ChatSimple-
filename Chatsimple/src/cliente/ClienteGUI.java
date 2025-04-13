package cliente;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Pattern;

/**
 * Cliente de chat con interfaz gráfica que implementa el patrón Observer
 * para actualizar dinámicamente la lista de usuarios conectados.
 */
public class ClienteGUI implements Observador {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    // Patrón para validación OWASP - previene inyección de comandos
    private static final Pattern PATRON_ENTRADA_SEGURA = Pattern.compile("^[a-zA-Z0-9\\s.,!?_\\-@()]*$");
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JList<String> listaUsuarios;
    private DefaultListModel<String> modeloUsuarios;
    private String username;
    private boolean conectado = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClienteGUI().iniciarCliente());
    }

    public void iniciarCliente() {
        try {
            username = solicitarNombreUsuario();
            if (username == null) {
                System.exit(0);
            }

            configurarVentana();
            conectarAlServidor();
        } catch (Exception e) {
            mostrarError("Error al iniciar el cliente: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private JButton attachButton;
    private JPanel inputPanel; 

    private void configurarVentana() {
        frame = new JFrame("Cliente de Chat - " + username);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BorderLayout());
        
        attachButton = new JButton("Adjuntar");
        attachButton.setBackground(new Color(0, 123, 255));
        attachButton.setForeground(Color.WHITE);
        attachButton.setFont(new Font("Arial", Font.BOLD, 14));
        
        JPanel inputPanel = new JPanel(new BorderLayout());

        

        attachButton.addActionListener(e -> enviarArchivo());

        // Panel de chat principal
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        
        // Panel de usuarios activos (implementación Observer)
        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane usuariosScrollPane = new JScrollPane(listaUsuarios);
        usuariosScrollPane.setBorder(BorderFactory.createTitledBorder("Usuarios conectados"));
        usuariosScrollPane.setPreferredSize(new Dimension(150, 0));
        
        // Panel de entrada
        inputPanel.add(attachButton, BorderLayout.WEST); // Añadir al panel de entrada
        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 14));
        sendButton = new JButton("Enviar");
        sendButton.setBackground(new Color(0, 123, 255));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Arial", Font.BOLD, 14));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        // Organizar componentes en la ventana
        JPanel panelCentral = new JPanel(new BorderLayout());
        panelCentral.add(scrollPane, BorderLayout.CENTER);
        
        frame.add(panelCentral, BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);
        frame.add(usuariosScrollPane, BorderLayout.EAST);

        // Configurar eventos
        sendButton.addActionListener(e -> enviarMensaje());
        inputField.addActionListener(e -> enviarMensaje());
        
        // Manejar el cierre de la ventana para desconectar adecuadamente
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
            }
        });

        frame.setVisible(true);
    }
    private void enviarArchivo() {
    JFileChooser fileChooser = new JFileChooser();
    int result = fileChooser.showOpenDialog(frame);
    if (result == JFileChooser.APPROVE_OPTION) {
        File archivo = fileChooser.getSelectedFile();
        try {
            // Enviar el nombre y el tamaño del archivo primero
            out.println("/enviarArchivo " + archivo.getName() + " " + archivo.length());
            out.flush();

            // Usar OutputStream para enviar el archivo
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(archivo));
                 OutputStream os = socket.getOutputStream()) {  // Usar OutputStream aquí
                byte[] buffer = new byte[4096];
                int bytesLeidos;
                while ((bytesLeidos = bis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesLeidos);  // Escribir datos binarios en el OutputStream
                }
                os.flush();  // Asegúrate de que se envíen todos los datos
            }
        } catch (IOException e) {
            mostrarError("Error al enviar el archivo: " + e.getMessage());
        }
    }
}


    private String solicitarNombreUsuario() {
        String nombre = null;
        boolean nombreValido = false;
        
        while (!nombreValido) {
            nombre = JOptionPane.showInputDialog(
                null, 
                "Ingrese su nombre de usuario:", 
                "Login", 
                JOptionPane.PLAIN_MESSAGE
            );
            
            if (nombre == null) {
                return null; // El usuario canceló
            }
            
            nombre = nombre.trim();
            
            if (nombre.isEmpty()) {
                mostrarError("El nombre de usuario no puede estar vacío");
            } else if (!validarEntrada(nombre)) {
                mostrarError("El nombre contiene caracteres no permitidos.\nUse solo letras, números y caracteres básicos.");
            } else {
                nombreValido = true;
            }
        }
        
        return nombre;
    }
    
    

    private void conectarAlServidor() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Enviar comando de login
            out.println("/login " + username);
            conectado = true;
            
            agregarMensaje("Sistema", "Conectado al servidor como " + username);
            
            // Iniciar thread para recibir mensajes
            new Thread(new ReceptorMensajes()).start();
            
        } catch (UnknownHostException e) {
            agregarMensaje("ERROR", "No se puede encontrar el host: " + SERVER_ADDRESS);
        } catch (IOException e) {
            agregarMensaje("ERROR", "No se pudo conectar al servidor: " + e.getMessage());
        }
    }

    private void enviarMensaje() {
        try {
            String texto = inputField.getText().trim();
            if (!texto.isEmpty() && conectado) {
                if (validarEntrada(texto)) {
                    out.println(username + ": " + texto);
                    inputField.setText("");
                } else {
                    mostrarError("El mensaje contiene caracteres no permitidos por seguridad");
                }
            }
        } catch (Exception e) {
            agregarMensaje("ERROR", "No se pudo enviar el mensaje: " + e.getMessage());
        }
    }

    private boolean validarEntrada(String texto) {
        // Validación OWASP para prevenir inyección de comandos
        return PATRON_ENTRADA_SEGURA.matcher(texto).matches();
    }

    private void agregarMensaje(String origen, String contenido) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[" + origen + "] " + contenido + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(
            frame,
            mensaje,
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    private void desconectar() {
        if (conectado) {
            try {
                out.println("/salir");
                conectado = false;
                
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
                
            } catch (IOException e) {
                System.err.println("Error al desconectar: " + e.getMessage());
            }
        }
    }

    /**
     * Implementación del método de la interfaz Observador
     * que actualiza la lista de usuarios cuando el servidor notifica cambios
     */
    @Override
    public void actualizarUsuarios(String listaUsuarios) {
        SwingUtilities.invokeLater(() -> {
            modeloUsuarios.clear();
            if (listaUsuarios != null && !listaUsuarios.isEmpty()) {
                String[] usuarios = listaUsuarios.split(",");
                for (String usuario : usuarios) {
                    if (!usuario.trim().isEmpty()) {
                        modeloUsuarios.addElement(usuario.trim());
                    }
                }
            }
            agregarMensaje("Sistema", "Lista de usuarios actualizada");
        });
    }

    /**
     * Clase interna para manejar la recepción de mensajes del servidor
     * en un hilo separado
     */
    private class ReceptorMensajes implements Runnable {
        @Override
        public void run() {
            try {
                String mensaje;
                while (conectado && (mensaje = in.readLine()) != null) {
                    if (mensaje.startsWith("/usuarios ")) {
                        // Notificación del patrón Observer
                        String lista = mensaje.substring(10);
                        actualizarUsuarios(lista);
                    } else {
                        // Mensajes normales
                        final String mensajeFinal = mensaje;
                        SwingUtilities.invokeLater(() -> 
                            chatArea.append(mensajeFinal + "\n")
                        );
                    }
                    // Auto-scroll al final
                    SwingUtilities.invokeLater(() -> 
                        chatArea.setCaretPosition(chatArea.getDocument().getLength())
                    );
                }
            } catch (SocketException e) {
                if (conectado) {
                    agregarMensaje("INFO", "Conexión perdida con el servidor");
                }
            } catch (IOException e) {
                agregarMensaje("ERROR", "Error al recibir mensajes: " + e.getMessage());
            } finally {
                conectado = false;
            }
        }
    }
}