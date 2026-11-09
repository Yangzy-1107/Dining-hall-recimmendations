package cn.edu.canteen;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImageService {
    private final Path directory;
    public ImageService(@Value("${app.upload-dir}") String directory) { this.directory = Path.of(directory).toAbsolutePath().normalize(); }

    public String save(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new BusinessException(400, "请选择不超过5MB的JPG或PNG图片");
        try {
            byte[] bytes = file.getBytes();
            String extension;
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new BusinessException(400, "文件内容不是有效的JPG或PNG图片");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    extension = switch (format) {
                        case "jpeg", "jpg" -> ".jpg";
                        case "png" -> ".png";
                        default -> throw new BusinessException(400, "仅支持JPG或PNG图片");
                    };
                    reader.setInput(stream);
                    reader.read(0);
                } finally { reader.dispose(); }
            }
            Files.createDirectories(directory);
            String name = UUID.randomUUID() + extension;
            Files.write(directory.resolve(name), bytes);
            return "/uploads/" + name;
        } catch (IOException ex) {
            throw new BusinessException(400, "图片读取或保存失败，请重新选择图片");
        }
    }

    public void checkReference(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.matches("/uploads/[0-9a-f-]{36}\\.(jpg|png)") || !Files.isRegularFile(directory.resolve(url.substring(9)))) {
            throw new BusinessException(400, "图片引用无效，请重新上传");
        }
    }
}
