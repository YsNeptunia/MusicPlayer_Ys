package app.musicplayer.view;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import app.musicplayer.MusicPlayer;
import app.musicplayer.model.Library;
import app.musicplayer.model.Song;
import app.musicplayer.util.ClippedTableCell;
import app.musicplayer.util.ControlPanelTableCell;
import app.musicplayer.util.PlayingTableCell;
import app.musicplayer.util.SubView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.Animation;
import javafx.animation.Transition;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableView.TableViewSelectionModel;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class StreamingController implements Initializable, SubView {
    @FXML private TableView<Song> tableView;
    @FXML private TableColumn<Song, Boolean> playingColumn;
    @FXML private TableColumn<Song, String> titleColumn;
    @FXML private TableColumn<Song, String> artistColumn;
    @FXML private TableColumn<Song, String> albumColumn;
    @FXML private TableColumn<Song, String> idColumn;
    @FXML private TableColumn<Song, Integer> playsColumn;
    @FXML private Label searchKey;

    private MusicPlayer musicPlayer;

    // Initializes table view scroll bar.
    private ScrollBar scrollBar;

    // Keeps track of which column is being used to sort table view and in what order (ascending or descending)
    private String currentSortColumn = "titleColumn";
    private String currentSortOrder = null;

    private Song selectedSong;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 移除所有与排序相关的设置
        titleColumn.setSortable(false);
        artistColumn.setSortable(false);
        albumColumn.setSortable(false);
        idColumn.setSortable(false);
//        playsColumn.setSortable(false);

        // 设置列宽比例（保持不变）
        titleColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(50).multiply(0.26));
        artistColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(50).multiply(0.26));
        albumColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(50).multiply(0.26));
        idColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(50).multiply(0.11));
        playsColumn.prefWidthProperty().bind(tableView.widthProperty().subtract(50).multiply(0.11));

        // 设置单元格工厂（保持不变）
        playingColumn.setCellFactory(x -> new PlayingTableCell<>());
        titleColumn.setCellFactory(x -> new ControlPanelTableCell<>());
        artistColumn.setCellFactory(x -> new ClippedTableCell<>());
        albumColumn.setCellFactory(x -> new ClippedTableCell<>());
        idColumn.setCellFactory(x -> new ClippedTableCell<>());
        playsColumn.setCellFactory(x -> new ClippedTableCell<>());

        // 设置单元格值工厂（保持不变）
        playingColumn.setCellValueFactory(new PropertyValueFactory<>("playing"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        artistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
        albumColumn.setCellValueFactory(new PropertyValueFactory<>("album"));

        // 为流媒体ID列设置绑定
        idColumn.setCellValueFactory(cellData ->
                cellData.getValue().streamingIdProperty());

        // 移除排序相关设置
        tableView.getSortOrder().clear();
        tableView.setSortPolicy(null);

        // 确保表格获取焦点（保持不变）
        tableView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            tableView.requestFocus();
            event.consume();
        });

        String keywords = "";
        //从txt中读取关键词
        try (BufferedReader br = new BufferedReader(new FileReader("./out/production/keyword.txt"))) {
            keywords = br.readLine(); // 读取第一行（即唯一的单词）
        } catch (IOException e) {
            e.printStackTrace(); // 捕获并打印异常
        }
        //在右上角的Label fx:id="searchKey"中填入字符串
        searchKey.setText("Search results of: \""+keywords+"\"");

        // 加载流媒体歌曲
        String jsonPath = new File("./out/production/api_search_results.json").getAbsolutePath();
        updateSongsListFromJSON(jsonPath);
        ObservableList<Song> songs = FXCollections.observableArrayList(Library.getSongs("test"));
//        ObservableList<Song> songs = Library.getSongs();

        // 设置表格数据
        tableView.setItems(songs);

        // 行工厂 - 保留点击监听器
        tableView.setRowFactory(x -> {
            TableRow<Song> row = new TableRow<>();

            PseudoClass playing = PseudoClass.getPseudoClass("playing");

            ChangeListener<Boolean> changeListener = (obs, oldValue, newValue) ->
                    row.pseudoClassStateChanged(playing, newValue);

            row.itemProperty().addListener((obs, previousSong, currentSong) -> {
                if (previousSong != null) {
                    previousSong.playingProperty().removeListener(changeListener);
                }
                if (currentSong != null) {
                    currentSong.playingProperty().addListener(changeListener);
                    row.pseudoClassStateChanged(playing, currentSong.getPlaying());
                } else {
                    row.pseudoClassStateChanged(playing, false);
                }
            });

            // 保留鼠标点击处理逻辑
            row.setOnMouseClicked(event -> {
                TableViewSelectionModel<Song> sm = tableView.getSelectionModel();
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    selectedSong = row.getItem();
                    play();
                } else if (event.isShiftDown()) {
                    // Shift+点击处理...
                } else if (event.isControlDown()) {
                    // Ctrl+点击处理...
                } else {
                    // 普通点击处理...
                }
            });

            row.setOnDragDetected(event -> {
                Dragboard db = row.startDragAndDrop(TransferMode.ANY);
                ClipboardContent content = new ClipboardContent();
                if (tableView.getSelectionModel().getSelectedIndices().size() > 1) {
                    content.putString("List");
                    db.setContent(content);
                    MusicPlayer.setDraggedItem(tableView.getSelectionModel().getSelectedItems());
                } else {
                    content.putString("Song");
                    db.setContent(content);
                    MusicPlayer.setDraggedItem(row.getItem());
                }
                ImageView image = new ImageView(row.snapshot(null, null));
                Rectangle2D rectangle = new Rectangle2D(0, 0, 250, 50);
                image.setViewport(rectangle);
                db.setDragView(image.snapshot(null, null), 125, 25);
                event.consume();
            });

            return row;
        });

        // 保留选中项监听器
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            // 选中项变化处理...
        });

        // 移除所有列的比较器设置（排序功能）
        // titleColumn.setComparator(...) 等代码已移除
    }
//    private int compareSongs(Song x, Song y) {
//        if (x == null && y == null) {
//            return 0;
//        } else if (x == null) {
//            return 1;
//        } else if (y == null) {
//            return -1;
//        }
//        if (x.getTitle() == null && y.getTitle() == null) {
//            // Both are equal.
//            return 0;
//        } else if (x.getTitle() == null) {
//            // Null is after other strings.
//            return 1;
//        } else if (y.getTitle() == null) {
//            // All other strings are before null.
//            return -1;
//        } else  /*(x.getTitle() != null && y.getTitle() != null)*/ {
//            return x.getTitle().compareTo(y.getTitle());
//        }
//    }

    @Override
    public void play() {
        MusicPlayer.pause();
        // 关闭之前的播放器（如果有）
        MusicPlayer.closeStreamingPlayer();
        // 获取当前选中的歌曲
        Song song = selectedSong;
        if (song == null) return;

        // 获取歌曲的流媒体ID
//        String streamingId = song.getStreamingId();

        // 创建WebView播放器
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);

        // 构建播放器HTML内容
        String playerHTML = "<!DOCTYPE html><html><body style='margin:0;padding:0;overflow:hidden;'>"
                + "<iframe src='https://music.163.com/outchain/player?type=2&id=" + selectedSong.getStreamingId()//2156184186
                + "&auto=1&height=66' width='280' height='86' frameborder='no' border='0'"
                + " marginwidth='0' marginheight='0' scrolling='no'></iframe></body></html>";

        engine.loadContent(playerHTML);

        // 创建播放器容器
        StackPane playerContainer = new StackPane(webView);
        playerContainer.setStyle("-fx-background-color: transparent;");

        // 创建播放器窗口
        Stage playerStage = new Stage();
        playerStage.initStyle(StageStyle.TRANSPARENT);
        playerStage.setScene(new Scene(playerContainer, 280, 86, Color.TRANSPARENT));

        // 设置窗口位置（覆盖本地播放栏）
        playerStage.setX(MusicPlayer.getStage().getX()); // 水平偏移量
        playerStage.setY(MusicPlayer.getStage().getY() + MusicPlayer.getStage().getHeight() - 145); // 垂直位置

        // 显示播放器
        playerStage.show();

        // 保存引用以便后续关闭
        MusicPlayer.setStreamingPlayer(playerStage, engine);
    }

//    @Override
//    public void play() {
//
//        Song song = selectedSong;
//        ObservableList<Song> songList = tableView.getItems();
//        if (MusicPlayer.isShuffleActive()) {
//            Collections.shuffle(songList);
//            songList.remove(song);
//            songList.add(0, song);
//        }
//        MusicPlayer.setNowPlayingList(songList);
//        MusicPlayer.setNowPlaying(song);
//        MusicPlayer.play();
//    }

    @Override
    public void scroll(char letter) {

        if (tableView.getSortOrder().size() > 0) {
            currentSortColumn = tableView.getSortOrder().get(0).getId();
            currentSortOrder = tableView.getSortOrder().get(0).getSortType().toString().toLowerCase();
        }

        // Retrieves songs from table.
        ObservableList<Song> songTableItems = tableView.getItems();
        // Initializes counter for cells. Used to determine what cell to scroll to.
        int selectedCell = 0;
        int selectedLetterCount = 0;

        // Retrieves the table view scroll bar.
        if (scrollBar == null) {
            scrollBar = (ScrollBar) tableView.lookup(".scroll-bar");
        }

        switch (currentSortColumn) {
            case "titleColumn":
                for (Song song : songTableItems) {
                    // Gets song title and compares first letter to selected letter.
                    String songTitle = song.getTitle();
                    try {
                        char firstLetter = songTitle.charAt(0);
                        if (firstLetter < letter) {
                            selectedCell++;
                        } else if (firstLetter == letter) {
                            selectedLetterCount++;
                        }
                    } catch (NullPointerException npe) {
                        System.out.println("Null Song Title");
                    }

                }
                break;
            case "artistColumn":
                for (Song song : songTableItems) {
                    // Removes article from song artist and compares it to selected letter.
                    String songArtist = song.getArtist();
                    try {
                        char firstLetter = removeArticle(songArtist).charAt(0);
                        if (firstLetter < letter) {
                            selectedCell++;
                        } else if (firstLetter == letter) {
                            selectedLetterCount++;
                        }
                    } catch (NullPointerException npe) {
                        System.out.println("Null Song Artist");
                    }
                }
                break;
            case "albumColumn":
                for (Song song : songTableItems) {
                    // Removes article from song album and compares it to selected letter.
                    String songAlbum = song.getAlbum();
                    try {
                        char firstLetter = removeArticle(songAlbum).charAt(0);
                        if (firstLetter < letter) {
                            selectedCell++;
                        } else if (firstLetter == letter) {
                            selectedLetterCount++;
                        }
                    } catch (NullPointerException npe) {
                        System.out.println("Null Song Album");
                    }
                }
                break;
        }

        double startVvalue = scrollBar.getValue();
        double finalVvalue;

        if ("descending".equals(currentSortOrder)) {
            finalVvalue = 1 - (((selectedCell + selectedLetterCount) * 50 - scrollBar.getHeight()) /
                    (songTableItems.size() * 50 - scrollBar.getHeight()));
        } else {
            finalVvalue = (double) (selectedCell * 50) / (songTableItems.size() * 50 - scrollBar.getHeight());
        }

        Animation scrollAnimation = new Transition() {
            {
                setCycleDuration(Duration.millis(500));
            }
            protected void interpolate(double frac) {
                double vValue = startVvalue + ((finalVvalue - startVvalue) * frac);
                scrollBar.setValue(vValue);
            }
        };
        scrollAnimation.play();
    }

    private String removeArticle(String title) {

        String arr[] = title.split(" ", 2);

        if (arr.length < 2) {
            return title;
        } else {

            String firstWord = arr[0];
            String theRest = arr[1];

            switch (firstWord) {
                case "A":
                case "An":
                case "The":
                    return theRest;
                default:
                    return title;
            }
        }
    }

    public Song getSelectedSong() {
        return selectedSong;
    }

    /**
     * 从 JSON 文件更新歌曲列表
     * @param jsonPath JSON 文件路径
     */
    private static void updateSongsListFromJSON(String jsonPath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(jsonPath));
            JsonNode arr = root.path("result").path("songs");
            if (!arr.isArray()) return;

            // 使用专用方法清空
            Library.clearStreamingSongs();

            for (JsonNode node : arr) {
                String id = node.path("id").asText();
                String title = node.path("name").asText();
                // 艺术家
                String artist = "";
                JsonNode ar = node.path("ar");
                if (ar.isArray() && ar.size() > 0) {
                    artist = ar.get(0).path("name").asText();
                }
                // 专辑
                String album = node.path("al").path("name").asText();

                if (!title.isEmpty() && !artist.isEmpty()) {
                    // 使用专用方法添加
                    Library.addStreamingSong(new Song(
                            title,
                            artist,
                            album,
                            id
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
