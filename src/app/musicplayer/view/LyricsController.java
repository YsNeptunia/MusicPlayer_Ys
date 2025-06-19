package app.musicplayer.view;

import app.musicplayer.MusicPlayer;
import app.musicplayer.model.Song;
import app.musicplayer.util.SubView; // 导入 SubView 接口
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LyricsController implements SubView {

    @FXML private TextArea lyricsTextArea; // 歌词显示区域
    @FXML private Label songTitleLabel;     // 歌曲标题标签
    @FXML private Label artistLabel;        // 艺术家标签

    /**
     * 初始化控制器
     */
    @FXML
    private void initialize() {
        // 配置文本区域
        lyricsTextArea.setWrapText(true);
        lyricsTextArea.setEditable(false);
        lyricsTextArea.setStyle("-fx-font-family: 'Microsoft YaHei', sans-serif; -fx-font-size: 16px;");
    }

    /**
     * 加载指定歌曲的歌词
     * @param song 当前播放的歌曲对象
     */
    public void loadLyrics(Song song) {
        if (song == null) {
            showNoSongError();
            return;
        }
        try {
            // 更新歌曲信息标签
            updateSongInfoLabels(song);

            // 从文件加载歌词（只处理TXT格式）
            String lyrics = LyricsLoader.loadLyrics(song);

            // 显示歌词（直接显示，无需处理时间标签）
            displayLyrics(lyrics);

        } catch (IOException e) {
            System.err.println("Failed to load lyrics: " + e.getMessage());
            e.printStackTrace();
            showLoadError();
        }
    }

    // 更新歌曲信息标签
    private void updateSongInfoLabels(Song song) {
        if (songTitleLabel != null && artistLabel != null) {
            songTitleLabel.setText(song.getTitle());
            artistLabel.setText(song.getArtist());
        }
    }

    /* 处理LRC格式歌词，移除时间标签 */
    private String processLrc(String lrc) {
        return lrc != null ?
                lrc.replaceAll("\\[\\d{1,2}:\\d{1,2}(\\.\\d{1,2})?\\]", "") :
                "";
    }

    /**
     * 显示歌词内容（同步版本）
     */
    private synchronized void displayLyrics(String lyrics) {
        if (lyrics == null || lyrics.trim().isEmpty()) {
            showNoLyricsFound();
        } else {
            lyricsTextArea.setText(lyrics);
            lyricsTextArea.setStyle("-fx-text-fill: #333;");
        }
    }

    /**
     * 显示无歌曲错误
     */
    public void showNoSongError() {
        if (songTitleLabel != null) songTitleLabel.setText("当前无播放歌曲");
        if (artistLabel != null) artistLabel.setText("");
        lyricsTextArea.setText("当前无播放歌曲");
        lyricsTextArea.setStyle("-fx-text-fill: #ff5722;");
    }

    /**
     * 显示未找到歌词提示
     */
    private void showNoLyricsFound() {
        lyricsTextArea.setText("未找到该歌曲的歌词");
        lyricsTextArea.setStyle("-fx-text-fill: #9e9e9e;");
    }

    /**
     * 显示加载错误
     */
    private void showLoadError() {
        lyricsTextArea.setText("歌词加载失败");
        lyricsTextArea.setStyle("-fx-text-fill: #f44336;");
    }

    /**
     * 歌词加载工具类
     */
    public static class LyricsLoader {
        public static String loadLyrics(Song song) throws IOException {
            if (song == null || song.getLocation() == null) {
                throw new IOException("歌曲或路径为空");
            }

            String songFilePath = song.getLocation();
            System.out.println("歌曲路径: " + songFilePath);

            // 生成歌词文件路径
            File songFile = new File(songFilePath);
            String baseName = songFile.getName().replaceFirst("\\.[^.]+$", "");
            String parentDir = songFile.getParent();

            // 处理父目录为空的情况
            if (parentDir == null) {
                parentDir = System.getProperty("user.dir");
                System.out.println("使用默认目录: " + parentDir);
            }

            // 只处理TXT格式
            String txtFilePath = parentDir + File.separator + baseName + ".txt";
            File txtFile = new File(txtFilePath);
            System.out.println("尝试加载TXT: " + txtFilePath);

            // 如果文件不存在，创建新的UTF-8编码文件
            if (!txtFile.exists()) {
                System.out.println("TXT文件不存在，创建新文件: " + txtFilePath);

                // 创建父目录（如果不存在）
                File parent = txtFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                // 创建包含歌曲基本信息的TXT文件
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(txtFile), StandardCharsets.UTF_8))) {
                    writer.write("歌曲: " + song.getTitle() + "\n");
                    writer.write("艺术家: " + song.getArtist() + "\n");
                    writer.write("\n歌词内容将显示在这里\n");
                    System.out.println("成功创建新歌词文件");
                }
            }

            // 读取文件（此时文件必定存在）
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(txtFile), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                System.out.println("成功加载TXT歌词，长度: " + sb.length() + " 字符");
                return sb.toString();
            }
        }
    }

    // ===== 实现 SubView 接口的方法 =====

    @Override
    public void play() {
        // 播放当前选中的歌曲
        Song song = MusicPlayer.getNowPlaying();
        if (song != null) {
            loadLyrics(song);
            MusicPlayer.play();
        } else {
            showNoSongError();
        }
    }

    @Override
    public Song getSelectedSong() {
        return null;
    }

    @Override
    public void scroll(char letter) {
        // 歌词视图不需要按字母滚动，可空实现或添加相关功能
        System.out.println("Lyrics view does not support scrolling by letter");
    }
}