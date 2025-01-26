import java.io.InputStream;
import java.io.OutputStream;

public class StreamPair {
    private InputStream inputStream;
    private OutputStream outputStream;

    public StreamPair(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }
}
