package app.musicplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.LogManager;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import app.musicplayer.model.Album;
import app.musicplayer.model.Artist;
import app.musicplayer.model.Library;
import app.musicplayer.model.Song;
import app.musicplayer.util.Resources;
import app.musicplayer.util.XMLEditor;
import app.musicplayer.view.ImportMusicDialogController;
import app.musicplayer.view.MainController;
import app.musicplayer.view.NowPlayingController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.web.WebEngine;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class MusicPlayer extends Application {

    private static MainController mainController;
    private static MediaPlayer mediaPlayer;
    private static ArrayList<Song> nowPlayingList;
    private static int nowPlayingIndex;
    private static Song nowPlaying;
    private static Timer timer;
    private static int timerCounter;
    private static int secondsPlayed;
    private static boolean isRepeatActive = false;
    private static boolean isLoopActive = false;
    private static boolean isShuffleActive = false;
    private static boolean isMuted = false;
    private static Object draggedItem;

    private static Stage stage;

    // 存储library.xml中的文件数量。
    // 然后在启动应用程序时将其与音乐目录中的文件数量进行比较，
    // 以确定是否需要通过添加或删除歌曲来更新xml文件。
    private static int xmlFileNum;

    // 存储分配给歌曲的最后一个id。
    // 这在删除其他歌曲后添加新歌曲时很重要，
    // 因为最后一个分配的id不一定等于xml文件中的歌曲数量，
    // 因为可能已经删除了歌曲。
    private static int lastIdAssigned;

    // 流媒体小播放器
    private static Stage streamingPlayer;
    private static WebEngine streamingEngine;

    public static void main(String[] args) {
        Application.launch(MusicPlayer.class);
    }

    @Override
    public void start(Stage stage) throws Exception {

        // 抑制将音乐库数据转换为xml文件时产生的警告。
        LogManager.getLogManager().reset();
        PrintStream dummyStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                // 无操作
            }
        });
        System.setOut(dummyStream);
        System.setErr(dummyStream);

        timer = new Timer();
        timerCounter = 0;
        secondsPlayed = 0;

        MusicPlayer.stage = stage;
        MusicPlayer.stage.setTitle("Music Player");
        MusicPlayer.stage.getIcons().add(new Image(this.getClass().getResource(Resources.IMG + "Icon.png").toString()));
        MusicPlayer.stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        try {
            // 从fxml文件加载主布局。
            FXMLLoader loader = new FXMLLoader(this.getClass().getResource(Resources.FXML + "SplashScreen.fxml"));
            VBox view = loader.load();

            // 显示包含布局的场景。
            Scene scene = new Scene(view);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();

            // 调用函数检查library.xml文件是否存在。如果不存在，则创建文件。
            checkLibraryXML();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }

        Thread thread = new Thread(() -> {
            // 从库中检索歌曲、专辑、艺术家和播放列表数据。
            Library.getSongs();
            Library.getAlbums();
            Library.getArtists();
            Library.getPlaylists();

            nowPlayingList = Library.loadPlayingList();

            if (nowPlayingList.isEmpty()) {

                Artist artist = Library.getArtists().get(0);

                for (Album album : artist.getAlbums()) {
                    nowPlayingList.addAll(album.getSongs());
                }

                Collections.sort(nowPlayingList, (first, second) -> {
                    Album firstAlbum = Library.getAlbum(first.getAlbum());
                    Album secondAlbum = Library.getAlbum(second.getAlbum());
                    if (firstAlbum.compareTo(secondAlbum) != 0) {
                        return firstAlbum.compareTo(secondAlbum);
                    } else {
                        return first.compareTo(second);
                    }
                });
            }

            nowPlaying = nowPlayingList.get(0);
            nowPlayingIndex = 0;
            nowPlaying.setPlaying(true);
            timer = new Timer();
            timerCounter = 0;
            secondsPlayed = 0;
            String path = nowPlaying.getLocation();
            Media media = new Media(Paths.get(path).toUri().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(0.5);
            mediaPlayer.setOnEndOfMedia(new SongSkipper());

//            File imgFolder = new File(Resources.JAR + "/img");
//            if (!imgFolder.exists()) {

//            // 改为使用类加载器检查资源是否存在
//            boolean resourcesMissing =
//                    MusicPlayer.class.getResource(Resources.IMG + "Icon.png") == null ||
//                            MusicPlayer.class.getResource(Resources.FXML + "SplashScreen.fxml") == null;
//
//            if (resourcesMissing) {
//                // 下载资源的线程...
//                Thread thread1 = new Thread(() -> {
//                    Library.getArtists().forEach(Artist::downloadArtistImage);
//                });
//
//                Thread thread2 = new Thread(() -> {
//                    Library.getAlbums().forEach(Album::downloadArtwork);
//                });
//
//                thread1.start();
//                thread2.start();
//            }
//
//            new Thread(() -> {
//                XMLEditor.getNewSongs().forEach(song -> {
//                    try {
//                        Library.getArtist(song.getArtist()).downloadArtistImage();
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                    }
//                });
//            }).start();
//
//            // 调用函数初始化主布局。
//            Platform.runLater(this::initMain);
//        });
            File imgFolder = new File(Resources.JAR + "/img");
            if (!imgFolder.exists()) {
//
//                Thread thread1 = new Thread(() -> {
//                    Library.getArtists().forEach(Artist::downloadArtistImage);
//                });
//
//                Thread thread2 = new Thread(() -> {
//                    Library.getAlbums().forEach(Album::downloadArtwork);
//                });
//
//                thread1.start();
//                thread2.start();
            }

            new Thread(() -> {
                XMLEditor.getNewSongs().forEach(song -> {
                    try {
                        Library.getArtist(song.getArtist()).downloadArtistImage();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }).start();

            // Calls the function to initialize the main layout.
            Platform.runLater(this::initMain);
        });

        thread.start();
    }

    private static void checkLibraryXML() {
        // 查找jar文件及其父文件夹的路径。
        File musicPlayerJAR = null;
        try {
            musicPlayerJAR = new File(MusicPlayer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        String jarFilePath = musicPlayerJAR.getParentFile().getPath();

        // 将文件路径赋值给Resources.java中设置的XML文件路径。
        Resources.JAR = jarFilePath + "/";

        // 指定library.xml文件及其位置。
        File libraryXML = new File(Resources.JAR + "library.xml");

        // 如果文件存在，检查音乐目录是否已更改。
        Path musicDirectory;
        if (libraryXML.exists()) {
            // 从xml文件中获取音乐目录路径，以便计算音乐目录中的文件数量并与xml文件中的数据进行比较。
            // 然后将其作为创建目录监视器时的参数传递。
            musicDirectory = xmlMusicDirPathFinder();

            // 处理音乐目录被重命名的情况。
            try {
                // 获取音乐目录中的文件数量和xml文件中保存的文件数量。
                // 这些值将用于确定是否需要更新xml文件。
                int musicDirFileNum = musicDirFileNumFinder(musicDirectory.toFile(), 0);
                xmlFileNum = xmlMusicDirFileNumFinder();

                // 如果xml文件中存储的文件数量与音乐目录中的文件数量不同。
                // 音乐库已更改；更新xml文件。
                if (musicDirFileNum != xmlFileNum) {
                    // 从保存的音乐目录更新xml文件。
                    updateLibraryXML(musicDirectory);
                }
                // NullPointerException由musicDirFileNumFinder()抛出。
                // 如果音乐目录被重命名，则会出现这种情况。
            } catch (NullPointerException npe) {
                createLibraryXML();
                // 获取xml文件中保存的文件数量。
                xmlFileNum = xmlMusicDirFileNumFinder();
                // 从xml文件中获取音乐目录路径，以便在创建目录监视器时作为参数传递。
                musicDirectory = xmlMusicDirPathFinder();
            }

            // 如果library.xml文件不存在，则从用户指定的音乐库位置创建文件。
        } else if (!libraryXML.exists()) {
            createLibraryXML();
            // 获取xml文件中保存的文件数量。
            xmlFileNum = xmlMusicDirFileNumFinder();
            // 从xml文件中获取音乐目录路径，以便在创建目录监视器时作为参数传递。
            musicDirectory = xmlMusicDirPathFinder();
        }
    }


    private static Path xmlMusicDirPathFinder() {
        try {
            // 创建xml文件的读取器。
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty("javax.xml.stream.isCoalescing", true);
            FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String element = null;
            String path = null;

            // 循环遍历xml文件，查找音乐目录文件路径。
            while(reader.hasNext()) {
                reader.next();
                if (reader.isWhiteSpace()) {
                    continue;
                } else if (reader.isStartElement()) {
                    element = reader.getName().getLocalPart();
                } else if (reader.isCharacters() && element.equals("path")) {
                    path = reader.getText();
                    break;
                }
            }
            // 关闭xml读取器。
            reader.close();

            return Paths.get(path);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static int xmlMusicDirFileNumFinder() {
        try {
            // 创建xml文件的读取器。
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty("javax.xml.stream.isCoalescing", true);
            FileInputStream is = new FileInputStream(new File(Resources.JAR + "library.xml"));
            XMLStreamReader reader = factory.createXMLStreamReader(is, "UTF-8");

            String element = null;
            String fileNum = null;

            // 循环遍历xml文件，查找音乐目录文件路径。
            while(reader.hasNext()) {
                reader.next();
                if (reader.isWhiteSpace()) {
                    continue;
                } else if (reader.isStartElement()) {
                    element = reader.getName().getLocalPart();
                } else if (reader.isCharacters() && element.equals("fileNum")) {
                    fileNum = reader.getText();
                    break;
                }
            }
            // 关闭xml读取器。
            reader.close();

            // 将文件数量转换为int并返回该值。
            return Integer.parseInt(fileNum);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static int musicDirFileNumFinder(File musicDirectory, int i) {
        // 列出音乐目录中的所有文件，并将它们存储在数组中。
        File[] files = musicDirectory.listFiles();

        // 遍历文件，如果找到文件则递增计数器。
        for (File file : files) {
            if (file.isFile() && Library.isSupportedFileType(file.getName())) {
                i++;
            } else if (file.isDirectory()) {
                i = musicDirFileNumFinder(file, i);
            }
        }
        return i;
    }

    private static void updateLibraryXML(Path musicDirectory) {
        // 为XMLEditor设置音乐目录。
        XMLEditor.setMusicDirectory(musicDirectory);

        // 检查是否需要向xml文件添加、删除或同时添加、删除歌曲，
        // 并执行相应的操作。
        XMLEditor.addDeleteChecker();
    }

    private static void createLibraryXML() {
        try {
            FXMLLoader loader = new FXMLLoader(MusicPlayer.class.getResource(Resources.FXML + "ImportMusicDialog.fxml"));
            BorderPane importView = loader.load();

            // 创建对话框Stage。
            Stage dialogStage = new Stage();
            dialogStage.setTitle("音乐播放器配置");
            // 强制用户将焦点集中在对话框上。
            dialogStage.initModality(Modality.WINDOW_MODAL);
            // 设置对话框的最小装饰。
            dialogStage.initStyle(StageStyle.UTILITY);
            // 防止对话框被重新调整大小。
            dialogStage.setResizable(false);
            dialogStage.initOwner(stage);

            // 在Stage中设置导入音乐对话框场景。
            dialogStage.setScene(new Scene(importView));

            // 将对话框设置到控制器中。
            ImportMusicDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);

            // 显示对话框并等待用户关闭它。
            dialogStage.showAndWait();

            // 检查音乐是否成功导入。否则关闭应用程序。
            boolean musicImported = controller.isMusicImported();
            if (!musicImported) {
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 初始化主布局。
     */
    private void initMain() {
        try {
            // 从fxml文件加载主布局。
            FXMLLoader loader = new FXMLLoader(this.getClass().getResource(Resources.FXML + "Main.fxml"));
            BorderPane view = loader.load();

            // 显示包含布局的场景。
            double width = stage.getScene().getWidth();
            double height = stage.getScene().getHeight();

            view.setPrefWidth(width);
            view.setPrefHeight(height);

            Scene scene = new Scene(view);
            stage.setScene(scene);

            // 给控制器提供对音乐播放器主应用程序的访问。
            mainController = loader.getController();
            mediaPlayer.volumeProperty().bind(mainController.getVolumeSlider().valueProperty().divide(200));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 定义一个内部类SongSkipper，实现Runnable接口
    private static class SongSkipper implements Runnable {
        // 实现Runnable接口的run方法
        @Override
        public void run() {
            skip();
        }
    }

    // 定义一个TimeUpdater类，继承自TimerTask
    private static class TimeUpdater extends TimerTask {
        // 定义一个length变量，表示当前播放的音频长度，单位为秒
        private int length = (int) getNowPlaying().getLengthInSeconds() * 4;

        // 重写run方法，定时更新时间标签和时间滑块
        @Override
        public void run() {
            // 在JavaFX的线程中执行
            Platform.runLater(() -> {
                // 如果timerCounter小于length，表示播放未结束
                if (timerCounter < length) {
                    // 如果timerCounter能被4整除，表示每4秒更新一次时间标签
                    if (++timerCounter % 4 == 0) {
                        mainController.updateTimeLabels();
                        secondsPlayed++;
                    }
                    // 如果时间滑块未被按下，则更新时间滑块
                    if (!mainController.isTimeSliderPressed()) {
                        mainController.updateTimeSlider();
                    }
                }
            });
        }
    }

    /**
     * 播放选定的歌曲。
     */
    public static void play() {
        if (mediaPlayer != null && !isPlaying()) {
            // 在本地播放开始时禁用流媒体播放器
            MusicPlayer.closeStreamingPlayer();
            mediaPlayer.play();
            timer.scheduleAtFixedRate(new TimeUpdater(), 0, 250);
            mainController.updatePlayPauseIcon(true);
        }
    }

    /**
     * 检查是否有歌曲正在播放。
     */
    public static boolean isPlaying() {
        return mediaPlayer != null && MediaPlayer.Status.PLAYING.equals(mediaPlayer.getStatus());
    }

    /**
     * 暂停选定的歌曲。
     */
    public static void pause() {
        if (isPlaying()) {
            mediaPlayer.pause();
            timer.cancel();
            timer = new Timer();
            mainController.updatePlayPauseIcon(false);
        }
    }

    public static void seek(int seconds) {
        if (mediaPlayer != null) {
            mediaPlayer.seek(new Duration(seconds * 1000));
            timerCounter = seconds * 4;
            mainController.updateTimeLabels();
        }
    }

    /**
     * 跳过歌曲。
     */
    public static void skip() {
        if (isRepeatActive) {   // 单曲循环情况，则只播放当前歌曲
            boolean isPlaying = isPlaying();
            mainController.updatePlayPauseIcon(isPlaying);
            setNowPlaying(nowPlayingList.get(nowPlayingIndex));
            if (isPlaying) {
                play();
            }
        }else if (nowPlayingIndex < nowPlayingList.size() - 1) {    //顺序播放情况，到最后一首时进入列表循环判断，在下一个elseif中判断
            boolean isPlaying = isPlaying();
            mainController.updatePlayPauseIcon(isPlaying);
            setNowPlaying(nowPlayingList.get(nowPlayingIndex + 1));
            if (isPlaying) {
                play();
            }
        } else if (isLoopActive) {  //列表循环情况，从第一首再来
            boolean isPlaying = isPlaying();
            mainController.updatePlayPauseIcon(isPlaying);
            nowPlayingIndex = 0;
            setNowPlaying(nowPlayingList.get(nowPlayingIndex));
            if (isPlaying) {
                play();
            }
        } else {    //顺序播放的最后一首结束，停止播放
            mainController.updatePlayPauseIcon(false);
            nowPlayingIndex = 0;
            setNowPlaying(nowPlayingList.get(nowPlayingIndex));
        }
    }

    public static void back() {
        if (timerCounter > 20 || nowPlayingIndex == 0) {
            mainController.initializeTimeSlider();
            seek(0);
        } else {
            boolean isPlaying = isPlaying();
            setNowPlaying(nowPlayingList.get(nowPlayingIndex - 1));
            if (isPlaying) {
                play();
            }
        }
    }
    public static void mute(boolean isMuted) {
        MusicPlayer.isMuted = !isMuted;
        if (mediaPlayer != null) {
            mediaPlayer.setMute(!isMuted);
        }
    }

    public static void toggleRepeat() {
        isRepeatActive = !isRepeatActive;
        // 如果启用单曲循环，则禁用普通循环
        if (isRepeatActive) {
            isLoopActive = false;
        }
    }

    public static boolean isRepeatActive() {
        return isRepeatActive;
    }

    public static void toggleLoop() {
        isLoopActive = !isLoopActive;
    }

    public static boolean isLoopActive() {
        return isLoopActive;
    }

    public static void toggleShuffle() {

        // 切换随机播放状态
        isShuffleActive = !isShuffleActive;

        // 如果随机播放状态为true，则打乱播放列表
        if (isShuffleActive) {
            Collections.shuffle(nowPlayingList);
        } else {
            // 如果随机播放状态为false，则按专辑排序播放列表
            Collections.sort(nowPlayingList, (first, second) -> {
                int result = Library.getAlbum(first.getAlbum()).compareTo(Library.getAlbum(second.getAlbum()));
                if (result != 0) {
                    return result;
                }
                result = Library.getAlbum(first.getAlbum()).compareTo(Library.getAlbum(second.getAlbum()));
                if (result != 0) {
                    return result;
                }
                result = first.compareTo(second);
                return result;
            });
        }

        // 更新当前播放索引
        nowPlayingIndex = nowPlayingList.indexOf(nowPlaying);

        // 如果当前视图控制器是NowPlayingController，则重新加载视图
        if (mainController.getSubViewController() instanceof NowPlayingController) {
            mainController.loadView("nowPlaying");
        }
    }

    public static boolean isShuffleActive() {
        return isShuffleActive;
    }

    public static void clearLoopAndShuffle() {  //当单曲循环按下时清除另外两个状态
        isLoopActive = false;
        isShuffleActive = false;

        // 随机播放状态为false，则按专辑排序播放列表
        Collections.sort(nowPlayingList, (first, second) -> {
            int result = Library.getAlbum(first.getAlbum()).compareTo(Library.getAlbum(second.getAlbum()));
            if (result != 0) {
                return result;
            }
            result = Library.getAlbum(first.getAlbum()).compareTo(Library.getAlbum(second.getAlbum()));
            if (result != 0) {
                return result;
            }
            result = first.compareTo(second);
            return result;
        });
    }

    public static Stage getStage() {
        return stage;
    }

    /**
     * 获取主控制器对象。
     * @return MainController
     */
    public static MainController getMainController() {
        return mainController;
    }

    /**
     * 获取当前正在播放的歌曲列表。
     * @return 当前正在播放的歌曲的ArrayList
     */
    public static ArrayList<Song> getNowPlayingList() {
        return nowPlayingList == null ? new ArrayList<>() : new ArrayList<>(nowPlayingList);
    }

    public static void addSongToNowPlayingList(Song song) {
        if (!nowPlayingList.contains(song)) {
            nowPlayingList.add(song);
            Library.savePlayingList();
        }
    }

    public static void setNowPlayingList(List<Song> list) {
        nowPlayingList = new ArrayList<>(list);
        Library.savePlayingList();
    }

    public static void setNowPlaying(Song song) {
        // 如果当前播放列表中包含该歌曲
        if (nowPlayingList.contains(song)) {

            // 更新播放次数
            updatePlayCount();
            // 获取该歌曲在当前播放列表中的索引
            nowPlayingIndex = nowPlayingList.indexOf(song);
            // 如果当前播放的歌曲不为空
            if (nowPlaying != null) {
                // 将当前播放的歌曲设置为不播放状态
                nowPlaying.setPlaying(false);
            }
            // 将当前播放的歌曲设置为该歌曲
            nowPlaying = song;
            // 将该歌曲设置为播放状态
            nowPlaying.setPlaying(true);
            // 如果媒体播放器不为空
            if (mediaPlayer != null) {
                // 停止媒体播放器
                mediaPlayer.stop();
            }
            // 如果定时器不为空
            if (timer != null) {
                // 取消定时器
                timer.cancel();
            }
            // 创建新的定时器
            timer = new Timer();
            // 定时器计数器置为0
            timerCounter = 0;
            // 已播放时间置为0
            secondsPlayed = 0;
            // 获取该歌曲的路径
            String path = song.getLocation();
            // 创建媒体对象
            Media media = new Media(Paths.get(path).toUri().toString());
            // 创建媒体播放器
            mediaPlayer = new MediaPlayer(media);
            // 将媒体播放器的音量与主控制器的音量滑块绑定
            mediaPlayer.volumeProperty().bind(mainController.getVolumeSlider().valueProperty().divide(200));
            // 设置媒体播放器的结束事件
            mediaPlayer.setOnEndOfMedia(new SongSkipper());
            // 设置媒体播放器的静音状态
            mediaPlayer.setMute(isMuted);
            // 更新主控制器的当前播放按钮
            mainController.updateNowPlayingButton();
            // 初始化主控制器的时间滑块
            mainController.initializeTimeSlider();
            // 初始化主控制器的时间标签
            mainController.initializeTimeLabels();
        }
    }

    private static void updatePlayCount() {
        if (nowPlaying != null) {
            int length = (int) nowPlaying.getLengthInSeconds();
            if ((100 * secondsPlayed / length) > 50) {
                nowPlaying.played();
            }
        }
    }

    public static Song getNowPlaying() {
        return nowPlaying;
    }

    public static String getTimePassed() {
        int secondsPassed = timerCounter / 4;
        int minutes = secondsPassed / 60;
        int seconds = secondsPassed % 60;
        return Integer.toString(minutes) + ":" + (seconds < 10 ? "0" + seconds : Integer.toString(seconds));
    }

    public static String getTimeRemaining() {
        long secondsPassed = timerCounter / 4;
        long totalSeconds = getNowPlaying().getLengthInSeconds();
        long secondsRemaining = totalSeconds - secondsPassed;
        long minutes = secondsRemaining / 60;
        long seconds = secondsRemaining % 60;
        return Long.toString(minutes) + ":" + (seconds < 10 ? "0" + seconds : Long.toString(seconds));
    }

    public static void setDraggedItem(Object item) {
        draggedItem = item;
    }

    public static Object getDraggedItem() {
        return draggedItem;
    }

    public static int getXMLFileNum() {
        return xmlFileNum;
    }

    public static void setXMLFileNum(int i) {
        xmlFileNum = i;
    }

    public static int getLastIdAssigned() {
        return lastIdAssigned;
    }

    public static void setLastIdAssigned(int i) {
        lastIdAssigned = i;
    }

    //新建与删除流媒体迷你播放器
    public static void setStreamingPlayer(Stage player, WebEngine engine) {
        if (streamingPlayer != null) {
            if (streamingPlayer != null) {
                // 先停止当前播放
                streamingEngine.load("about:blank");
                streamingPlayer.close();
            }
        }
        streamingPlayer = player;
        streamingEngine = engine;
    }

    public static void closeStreamingPlayer() {
        if (streamingPlayer != null) {
            // 加载空白页面停止播放
            streamingEngine.load("about:blank");
            streamingPlayer.close();
            streamingPlayer = null;
            streamingEngine = null;
        }
    }
}
