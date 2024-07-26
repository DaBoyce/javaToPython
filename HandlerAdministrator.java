import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;

public class HandlerAdministrator {
    private HashMap<Integer, PythonHandler> handlers;

    public HandlerAdministrator() {
        handlers = new HashMap<>();
    }

    public void addHandler(int uniqueID, PythonHandler handler) {
        handlers.put(uniqueID, handler);
    }

    public PythonHandler getHandler(int uniqueID) {
        return handlers.get(uniqueID);
    }

    public static void main(String[] args) {
        HandlerAdministrator admin = new HandlerAdministrator();
        PythonHandler handler = new PythonHandler();
        admin.addHandler(1, handler);

        // Create and send prompt packets
        for (int i = 0; i < 5; i++) {
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB); // Dummy image
            PromptPacket packet = new PromptPacket(i, "Prompt " + i, image);
            System.out.println("Sending: " + packet.getPromptText());
            PromptPacket response = handler.sendPromptPacket(packet);
            System.out.println("Received: " + response.getPromptText());
        }
        
        handler.close(); // Close the handler when done
    }
}

class PromptPacket {
    private int uniqueID;
    private String promptText;
    private BufferedImage promptImage;

    public PromptPacket(int uniqueID, String promptText, BufferedImage promptImage) {
        this.uniqueID = uniqueID;
        this.promptText = promptText;
        this.promptImage = promptImage;
    }

    public int getUniqueID() {
        return uniqueID;
    }

    public String getPromptText() {
        return promptText;
    }

    public BufferedImage getPromptImage() {
        return promptImage;
    }

    public String toSerializedString() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(promptImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            return String.format("{\"uniqueID\":%d,\"promptText\":\"%s\",\"promptImage\":\"%s\"}", 
                                 uniqueID, promptText, base64Image);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static PromptPacket fromSerializedString(String str) {
        try {
            // Proper JSON parsing (consider using a proper JSON library for production)
            str = str.trim(); // Ensure no leading/trailing spaces
            String[] parts = str.substring(1, str.length() - 1).split(","); // Remove curly braces
            int id = Integer.parseInt(parts[0].split(":")[1].trim());
            String text = parts[1].split(":")[1].replace("\"", "").trim();
            String base64Image = parts[2].split(":")[1].replace("\"", "").replace("}", "").trim();
            
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);
            return new PromptPacket(id, text, image);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}

class PythonHandler {
    private Process pythonProcess;
    private BufferedWriter toPython;
    private BufferedReader fromPython;

    public PythonHandler() {
        startPythonScript();
    }

    private void startPythonScript() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "pythonscript.py");
            pythonProcess = pb.start();
            toPython = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));
            fromPython = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()));

            // Wait for Python script to signal it's ready
            String readySignal = fromPython.readLine();
            if (!"READY".equals(readySignal)) {
                throw new IOException("Python script failed to initialize");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PromptPacket sendPromptPacket(PromptPacket packet) {
        try {
            // Send the prompt packet
            toPython.write("QueryModel(" + packet.toSerializedString() + ")\n");
            toPython.flush();

            // Read and return the response
            String response = fromPython.readLine();
            return PromptPacket.fromSerializedString(response);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void close() {
        try {
            toPython.close();
            fromPython.close();
            pythonProcess.destroy();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}