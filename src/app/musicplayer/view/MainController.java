package app.musicplayer.view;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;

import app.musicplayer.model.*;
import app.musicplayer.util.Search;
import com.melloware.jintellitype.IntellitypeListener;
import com.melloware.jintellitype.JIntellitype;

import app.musicplayer.MusicPlayer;
import app.musicplayer.util.CustomSliderSkin;
import app.musicplayer.util.Resources;
import app.musicplayer.util.SubView;
import javafx.animation.Animation;
import javafx.animation.Animation.Status;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import app.musicplayer.util.ApiSearchTask;

public class MainController implements Initializable, IntellitypeListener {

	private boolean isSideBarExpanded = true;
	private boolean isRepeatActive = false;
    private double expandedWidth = 250;
    private double collapsedWidth = 50;
    private double expandedHeight = 50;
    private double collapsedHeight = 0;
	private double searchExpanded = 180;
	private double searchCollapsed = 0;
    private SubView subViewController;
    private CustomSliderSkin sliderSkin;
    private Stage volumePopup;
    private Stage searchPopup;
    private VolumePopupController volumePopupController;
    private CountDownLatch viewLoadedLatch;

    @FXML private ScrollPane subViewRoot;
    @FXML private VBox sideBar;
    @FXML private VBox playlistBox;
    @FXML private ImageView nowPlayingArtwork;
    @FXML private Label nowPlayingTitle;
    @FXML private Label nowPlayingArtist;
    @FXML private Slider timeSlider;
    @FXML private Region frontSliderTrack;    
    @FXML private Label timePassed;
    @FXML private Label timeRemaining;

    @FXML private HBox letterBox;
    @FXML private Separator letterSeparator;

    @FXML private Pane playButton;
    @FXML private Pane pauseButton;
    @FXML private Pane loopButton;
	@FXML private Pane repeatButton;
    @FXML private Pane shuffleButton;
    @FXML private HBox controlBox;

	@FXML private TextField searchBox;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    	// 重置锁存器
    	resetLatch();

    	// 移除控制框中的第三个元素
    	controlBox.getChildren().remove(2);

    	// 绑定时间滑块轨道的宽度
    	frontSliderTrack.prefWidthProperty().bind(timeSlider.widthProperty().multiply(timeSlider.valueProperty().divide(timeSlider.maxProperty())));

    	// 创建自定义滑块皮肤
    	sliderSkin = new CustomSliderSkin(timeSlider);
    	timeSlider.setSkin(sliderSkin);

    	// 创建音量弹出框和搜索弹出框
    	createVolumePopup();
        createSearchPopup();

    	// 获取伪类
    	PseudoClass active = PseudoClass.getPseudoClass("active");
    	// 循环按钮点击事件
    	loopButton.setOnMouseClicked(x -> {
			if(isRepeatActive)	return;
    		sideBar.requestFocus();
    		MusicPlayer.toggleLoop();
    		loopButton.pseudoClassStateChanged(active, MusicPlayer.isLoopActive());
    	});
    	// 随机播放按钮点击事件
    	shuffleButton.setOnMouseClicked(x -> {
			if(isRepeatActive)	return;
    		sideBar.requestFocus();
    		MusicPlayer.toggleShuffle();
    		shuffleButton.pseudoClassStateChanged(active, MusicPlayer.isShuffleActive());
    	});

		// 单曲循环按钮点击事件
		repeatButton.setOnMouseClicked(x -> {
			sideBar.requestFocus();
			isRepeatActive = !isRepeatActive;
			MusicPlayer.clearLoopAndShuffle();
			MusicPlayer.toggleRepeat();
			loopButton.setDisable(isRepeatActive);
			shuffleButton.setDisable(isRepeatActive);
			loopButton.pseudoClassStateChanged(active, false);
			shuffleButton.pseudoClassStateChanged(active, false);
			repeatButton.pseudoClassStateChanged(active, MusicPlayer.isRepeatActive());
		});

    	// 设置时间滑块不可聚焦
    	timeSlider.setFocusTraversable(false);

        // 时间滑块值改变事件
        timeSlider.valueChangingProperty().addListener(
            (slider, wasChanging, isChanging) -> {

                if (wasChanging) {

                    // 计算秒数
                    int seconds = (int) Math.round(timeSlider.getValue() / 4.0);
                    timeSlider.setValue(seconds * 4);
                    MusicPlayer.seek(seconds);
                }
            }
        );

        // 时间滑块值改变事件
        timeSlider.valueProperty().addListener(
            (slider, oldValue, newValue) -> {

                double previous = oldValue.doubleValue();
                double current = newValue.doubleValue();
                if (!timeSlider.isValueChanging() && current != previous + 1 && !isTimeSliderPressed()) {

                    // 计算秒数
                    int seconds = (int) Math.round(current / 4.0);
                    timeSlider.setValue(seconds * 4);
                    MusicPlayer.seek(seconds);
                }
            }
        );

        // 加载字母动画完成事件
        unloadLettersAnimation.setOnFinished(x -> {
        	letterBox.setPrefHeight(0);
        	letterSeparator.setPrefHeight(0);
		});

		// 搜索框文本改变事件
		searchBox.textProperty().addListener((observable, oldText, newText) -> {
			String text = newText.trim();
			if (text.equals("")) {
                // 如果搜索弹出框正在显示且搜索隐藏动画未运行，则播放搜索隐藏动画
                if (searchPopup.isShowing() && !searchHideAnimation.getStatus().equals(Status.RUNNING)) {
                    searchHideAnimation.play();
                }
            } else {
                // 搜索文本
                Search.search(text);
			}
		});

		// 搜索结果改变事件
		Search.hasResultsProperty().addListener((observable, hadResults, hasResults) -> {
			if (hasResults) {
                // 获取搜索结果
                SearchResult result = Search.getResult();
                // 在主线程中显示搜索结果
                Platform.runLater(() -> {
                    showSearchResults(result);
                    MusicPlayer.getStage().toFront();
                });
                // 计算搜索结果的高度
                int height = 0;
                int artists = result.getArtistResults().size();
                int albums = result.getAlbumResults().size();
                int songs = result.getSongResults().size();
                if (artists > 0) height += (artists * 50) + 50;
                if (albums > 0) height += (albums * 50) + 50;
                if (songs > 0) height += (songs * 50) + 50;
                if (height == 0) height = 50;

				boolean hasLocalResults = (artists > 0 || albums > 0 || songs > 0);

				// 增加网络搜索控件高度 (50px) 和分隔线高度 (20px)
				height += 50 + (hasLocalResults ? 20 : 0);

				// 没有本地结果时增加"No Results"标签高度
				if (!hasLocalResults) height += 50;

				// 设置搜索弹出框的高度
                searchPopup.setHeight(height);
            }
		});

		// 音乐播放器窗口x坐标改变事件
		MusicPlayer.getStage().xProperty().addListener((observable, oldValue, newValue) -> {
            // 如果搜索弹出框正在显示且搜索隐藏动画未运行，则播放搜索隐藏动画
            if (searchPopup.isShowing() && !searchHideAnimation.getStatus().equals(Status.RUNNING)) {
                searchHideAnimation.play();
            }
        });

        // 音乐播放器窗口y坐标改变事件
        MusicPlayer.getStage().yProperty().addListener((observable, oldValue, newValue) -> {
            // 如果搜索弹出框正在显示且搜索隐藏动画未运行，则播放搜索隐藏动画
            if (searchPopup.isShowing() && !searchHideAnimation.getStatus().equals(Status.RUNNING)) {
                searchHideAnimation.play();
            }
        });

		// 设置字母框中每个标签的宽度
		for (Node node : letterBox.getChildren()) {
        	Label label = (Label)node;
        	label.prefWidthProperty().bind(letterBox.widthProperty().subtract(50).divide(26).subtract(1));
        }

        // 更新正在播放按钮
        updateNowPlayingButton();
        // 初始化时间滑块
        initializeTimeSlider();
        // 初始化时间标签
        initializeTimeLabels();
        // 初始化播放列表
        initializePlaylists();

        // Register media keys on Windows
        if (System.getProperty("os.name").toUpperCase().contains("WINDOWS")) {
        	JIntellitype.getInstance().addIntellitypeListener(this);
        }

        // Loads the default view: artists.
        loadView("artists");
    }
    
    @Override
    public void onIntellitype(int key) {
    	// Skip/play/pause/back using Windows media keys
    	Platform.runLater(() -> {
    		switch (key) {
        	case JIntellitype.APPCOMMAND_MEDIA_NEXTTRACK:
        		skip();
        		break;
        	case JIntellitype.APPCOMMAND_MEDIA_PLAY_PAUSE:
        		playPause();
        		break;
        	case JIntellitype.APPCOMMAND_MEDIA_PREVIOUSTRACK:
        		back();
        		break;
        	}
    	});
    }
    
    void resetLatch() {
    	viewLoadedLatch = new CountDownLatch(1);
    }
    
    CountDownLatch getLatch() {
    	return viewLoadedLatch;
    }
    
    private void createVolumePopup() {
    	try {

    		Stage stage = MusicPlayer.getStage();
        	FXMLLoader loader = new FXMLLoader(this.getClass().getResource(Resources.FXML + "VolumePopup.fxml"));
        	HBox view = loader.load();
        	volumePopupController = loader.getController();
        	Stage popup = new Stage();
        	popup.setScene(new Scene(view));
        	popup.initStyle(StageStyle.UNDECORATED);
        	popup.initOwner(stage);
        	popup.setX(stage.getWidth() - 270);
        	popup.setY(stage.getHeight() - 120);
        	popup.focusedProperty().addListener((x, wasFocused, isFocused) -> {
        		if (wasFocused && !isFocused) {
        			volumeHideAnimation.play();
        		}
        	});
        	volumeHideAnimation.setOnFinished(x -> popup.hide());

        	popup.show();
        	popup.hide();
        	volumePopup = popup;

    	} catch (Exception ex) {

    		ex.printStackTrace();
    	}
    }

	private void createSearchPopup() {
		try {

			Stage stage = MusicPlayer.getStage();
			VBox view = new VBox();
            view.getStylesheets().add(Resources.CSS + "MainStyle.css");
            view.getStyleClass().add("searchPopup");
			Stage popup = new Stage();
			popup.setScene(new Scene(view));
			popup.initStyle(StageStyle.UNDECORATED);
			popup.initOwner(stage);
			searchHideAnimation.setOnFinished(x -> popup.hide());

			popup.show();
			popup.hide();
			searchPopup = popup;

		} catch (Exception ex) {

			ex.printStackTrace();
		}
	}
    
    public void updateNowPlayingButton() {

        Song song = MusicPlayer.getNowPlaying();
        if (song != null) {
            nowPlayingTitle.setText(song.getTitle());
            nowPlayingArtist.setText(song.getArtist());
            nowPlayingArtwork.setImage(song.getArtwork());
        } else {
            nowPlayingTitle.setText("");
            nowPlayingArtist.setText("");
            nowPlayingArtwork.setImage(null);
        }
    }

    public void initializeTimeSlider() {

        Song song = MusicPlayer.getNowPlaying();
        if (song != null) {
            timeSlider.setMin(0);
            timeSlider.setMax(song.getLengthInSeconds() * 4);
            timeSlider.setValue(0);
            timeSlider.setBlockIncrement(1);
        } else {
            timeSlider.setMin(0);
            timeSlider.setMax(1);
            timeSlider.setValue(0);
            timeSlider.setBlockIncrement(1);
        }
    }

    public void updateTimeSlider() {

        timeSlider.increment();
    }

    public void initializeTimeLabels() {

        Song song = MusicPlayer.getNowPlaying();
        if (song != null) {
            timePassed.setText("0:00");
            timeRemaining.setText(song.getLength());
        } else {
            timePassed.setText("");
            timeRemaining.setText("");
        }
    }

    public void updateTimeLabels() {

        timePassed.setText(MusicPlayer.getTimePassed());
        timeRemaining.setText(MusicPlayer.getTimeRemaining());
    }
    
    @SuppressWarnings("unchecked")
	private void initializePlaylists() {
    	// 遍历Library中的所有歌单
    	for (Playlist playlist : Library.getPlaylists()) {
    		try {
    			// 加载歌单单元格的FXML文件
    			FXMLLoader loader = new FXMLLoader(this.getClass().getResource(Resources.FXML + "PlaylistCell.fxml"));
				HBox cell = loader.load();
				// 获取单元格中的标签
				Label label = (Label) cell.getChildren().get(1);
				// 设置标签的文本为歌单的标题
				label.setText(playlist.getTitle());

				// 设置单元格的鼠标点击事件
				cell.setOnMouseClicked(x -> {
					selectView(x);
					// 选中歌单
					((PlaylistsController) subViewController).selectPlaylist(playlist);
				});

				// 设置单元格的拖拽事件
				cell.setOnDragDetected(event -> {
					PseudoClass pressed = PseudoClass.getPseudoClass("pressed");
					// 设置单元格的伪类状态
					cell.pseudoClassStateChanged(pressed, false);
    	        	// 开始拖拽
    	        	Dragboard db = cell.startDragAndDrop(TransferMode.ANY);
    	        	ClipboardContent content = new ClipboardContent();
    	            content.putString("Playlist");
    	            db.setContent(content);
    	        	// 设置拖拽的项目
    	        	MusicPlayer.setDraggedItem(playlist);
    	        	// 设置拖拽的视图
    	        	db.setDragView(cell.snapshot(null, null), 125, 25);
    	            event.consume();
    	        });

				// 设置单元格的伪类状态
				PseudoClass hover = PseudoClass.getPseudoClass("hover");

				// 设置单元格的拖拽进入事件
				cell.setOnDragEntered(event -> {
					if (!(playlist instanceof MostPlayedPlaylist)
							&& !(playlist instanceof RecentlyPlayedPlaylist)
							&& event.getGestureSource() != cell
							&& event.getDragboard().hasString()) {

						cell.pseudoClassStateChanged(hover, true);
						//cell.getStyleClass().setAll("sideBarItemSelected");
					}
				});

				// 设置单元格的拖拽退出事件
				cell.setOnDragExited(event -> {
					if (!(playlist instanceof MostPlayedPlaylist)
							&& !(playlist instanceof RecentlyPlayedPlaylist)
							&& event.getGestureSource() != cell
							&& event.getDragboard().hasString()) {

						cell.pseudoClassStateChanged(hover, false);
						//cell.getStyleClass().setAll("sideBarItem");
					}
				});

				// 设置单元格的拖拽覆盖事件
				cell.setOnDragOver(event -> {
					if (!(playlist instanceof MostPlayedPlaylist)
							&& !(playlist instanceof RecentlyPlayedPlaylist)
							&& event.getGestureSource() != cell
							&& event.getDragboard().hasString()) {

						event.acceptTransferModes(TransferMode.ANY);
					}
					event.consume();
				});

				// 设置单元格的拖拽释放事件
				cell.setOnDragDropped(event -> {
					String dragString = event.getDragboard().getString();
					new Thread(() -> {
						switch (dragString) {
			            case "Artist":
			            	Artist artist = (Artist) MusicPlayer.getDraggedItem();
				            for (Album album : artist.getAlbums()) {
				            	for (Song song : album.getSongs()) {
				            		if (!playlist.getSongs().contains(song)) {
						            	playlist.addSong(song);
				            		}
					            }
				            }
				            break;
			            case "Album":
			            	Album album = (Album) MusicPlayer.getDraggedItem();
				            for (Song song : album.getSongs()) {
				            	if (!playlist.getSongs().contains(song)) {
					            	playlist.addSong(song);
			            		}
				            }
				            break;
			            case "Playlist":
			            	Playlist list = (Playlist) MusicPlayer.getDraggedItem();
				            for (Song song : list.getSongs()) {
				            	if (!playlist.getSongs().contains(song)) {
					            	playlist.addSong(song);
			            		}
				            }
				            break;
			            case "Song":
			            	Song song = (Song) MusicPlayer.getDraggedItem();
			            	if (!playlist.getSongs().contains(song)) {
				            	playlist.addSong(song);
		            		}
				            break;
			            case "List":
							ObservableList<Song> songs = (ObservableList<Song>) MusicPlayer.getDraggedItem();
			            	for (Song s : songs) {
			            		if (!playlist.getSongs().contains(s)) {
					            	playlist.addSong(s);
			            		}
			            	}
			            	break;
			            }
					}).start();

					event.consume();
				});

				// 将单元格添加到歌单框中
				playlistBox.getChildren().add(cell);

			} catch (Exception e) {

				e.printStackTrace();
			}
    	}
    }
    
    @FXML
    private void selectView(Event e) {

        // 获取事件源
        HBox eventSource = ((HBox)e.getSource());

        // 设置焦点
        eventSource.requestFocus();

        // 获取之前选中的节点
        Optional<Node> previous = sideBar.getChildren().stream()
            .filter(x -> x.getStyleClass().get(0).equals("sideBarItemSelected")).findFirst();

        // 如果之前有选中的节点
        if (previous.isPresent()) {
            // 将之前选中的节点样式设置为未选中
            HBox previousItem = (HBox) previous.get();
            previousItem.getStyleClass().setAll("sideBarItem");
        } else {
        	// 如果之前没有选中的节点，则从playlistBox中获取
        	previous = playlistBox.getChildren().stream()
                    .filter(x -> x.getStyleClass().get(0).equals("sideBarItemSelected")).findFirst();
        	// 如果playlistBox中有选中的节点
        	if (previous.isPresent()) {
                // 将之前选中的节点样式设置为未选中
                HBox previousItem = (HBox) previous.get();
                previousItem.getStyleClass().setAll("sideBarItem");
            }
        }

        // 获取当前节点的样式
        ObservableList<String> styles = eventSource.getStyleClass();

        // 如果当前节点样式为未选中
        if (styles.get(0).equals("sideBarItem")) {
            // 将当前节点样式设置为选中
            styles.setAll("sideBarItemSelected");
            // 加载视图
            loadView(eventSource.getId());
        // 如果当前节点样式为底部栏项
        } else if (styles.get(0).equals("bottomBarItem")) {
            // 加载视图
            loadView(eventSource.getId());
        }
    }
	@FXML
	private void navigateToSong() {
		clearPreviousSelection();
		selectLyricsItem();

		// 加载视图并获取控制器
		LyricsController lyricsController = (LyricsController) loadView("Lyrics");
		if (lyricsController == null) {
			System.err.println("Failed to load LyricsController");
			return;
		}

		Platform.runLater(() -> {
			// 获取当前播放歌曲并添加日志
			Song song = MusicPlayer.getNowPlaying();
			System.out.println("Current song in navigateToCurrentSong: " +
					(song != null ? song.getTitle() : "NULL"));
			if (song != null) {
				// 调用加载歌词方法
				lyricsController.loadLyrics(song);
			} else {
				lyricsController.showNoSongError();
			}
		});
	}

	// 提取清除选中状态的逻辑到单独方法
	private void clearPreviousSelection() {
		Optional<Node> previous = sideBar.getChildren().stream()
				.filter(x -> x.getStyleClass().contains("sideBarItemSelected"))
				.findFirst();

		if (previous.isPresent()) {
			HBox previousItem = (HBox) previous.get();
			previousItem.getStyleClass().setAll("sideBarItem");
		} else {
			previous = playlistBox.getChildren().stream()
					.filter(x -> x.getStyleClass().contains("sideBarItemSelected"))
					.findFirst();
			if (previous.isPresent()) {
				HBox previousItem = (HBox) previous.get();
				previousItem.getStyleClass().setAll("sideBarItem");
			}
		}
	}

	// 提取选择歌词项的逻辑到单独方法
	private void selectLyricsItem() {
		sideBar.getChildren().stream()
				.filter(node -> node.getStyleClass().contains("sideBarItemLyrics"))
				.findFirst()
				.ifPresent(node -> {
					node.getStyleClass().setAll("sideBarItemSelected");
				});
	}
    
    @SuppressWarnings("unchecked")
	@FXML
    private void newPlaylist() {

    	if (!newPlaylistAnimation.getStatus().equals(Status.RUNNING)) {

    		try {

    			FXMLLoader loader = new FXMLLoader(this.getClass().getResource(Resources.FXML + "PlaylistCell.fxml"));
    			HBox cell = loader.load();

    			Label label = (Label) cell.getChildren().get(1);
    			label.setVisible(false);
    			HBox.setMargin(label, new Insets(0, 0, 0, 0));

    			TextField textBox = new TextField();
    			textBox.setPrefHeight(30);
    			cell.getChildren().add(textBox);
    			HBox.setMargin(textBox, new Insets(10, 10, 10, 9));

    			textBox.focusedProperty().addListener((obs, oldValue, newValue) -> {
    				if (oldValue && !newValue) {
    					String text = textBox.getText().equals("") ? "New Playlist" : textBox.getText();
    					text = checkDuplicatePlaylist(text, 0);
    					label.setText(text);
        				cell.getChildren().remove(textBox);
        				HBox.setMargin(label, new Insets(10, 10, 10, 10));
        				label.setVisible(true);
        				Library.addPlaylist(text);
    				}
    			});

    			textBox.setOnKeyPressed(x -> {
    				if (x.getCode() == KeyCode.ENTER)  {
    		            sideBar.requestFocus();
    		        }
    			});

    			cell.setOnMouseClicked(x -> {
    				selectView(x);
    				Playlist playlist = Library.getPlaylist(label.getText());
    				((PlaylistsController) subViewController).selectPlaylist(playlist);
    			});

    			cell.setOnDragDetected(event -> {
    				PseudoClass pressed = PseudoClass.getPseudoClass("pressed");
					cell.pseudoClassStateChanged(pressed, false);
    				Playlist playlist = Library.getPlaylist(label.getText());
    	        	Dragboard db = cell.startDragAndDrop(TransferMode.ANY);
    	        	ClipboardContent content = new ClipboardContent();
    	            content.putString("Playlist");
    	            db.setContent(content);
    	        	MusicPlayer.setDraggedItem(playlist);
    	        	SnapshotParameters sp = new SnapshotParameters();
    	        	sp.setTransform(Transform.scale(1.5, 1.5));
    	        	db.setDragView(cell.snapshot(sp, null));
    	            event.consume();
    	        });

    			PseudoClass hover = PseudoClass.getPseudoClass("hover");

    			cell.setOnDragEntered(event -> {
    				Playlist playlist = Library.getPlaylist(label.getText());
					if (!(playlist instanceof MostPlayedPlaylist)
							&& !(playlist instanceof RecentlyPlayedPlaylist)
							&& event.getGestureSource() != cell
							&& event.getDragboard().hasString()) {

						cell.pseudoClassStateChanged(hover, true);
					}
				});

				cell.setOnDragExited(event -> {
					Playlist playlist = Library.getPlaylist(label.getText());
					if (!(playlist instanceof MostPlayedPlaylist)
							&& !(playlist instanceof RecentlyPlayedPlaylist)
							&& event.getGestureSource() != cell
							&& event.getDragboard().hasString()) {

						cell.pseudoClassStateChanged(hover, false);
					}
				});

				cell.setOnDragOver(event -> {
					Playlist playlist = Library.getPlaylist(label.getText());
					if (!(playlist instanceof MostPlayedPlaylist)
							&& !(playlist instanceof RecentlyPlayedPlaylist)
							&& event.getGestureSource() != cell
							&& event.getDragboard().hasString()) {

						event.acceptTransferModes(TransferMode.ANY);
					}
					event.consume();
				});

				cell.setOnDragDropped(event -> {
					Playlist playlist = Library.getPlaylist(label.getText());
					String dragString = event.getDragboard().getString();
					new Thread(() -> {
						switch (dragString) {
			            case "Artist":
			            	Artist artist = (Artist) MusicPlayer.getDraggedItem();
				            for (Album album : artist.getAlbums()) {
				            	for (Song song : album.getSongs()) {
				            		if (!playlist.getSongs().contains(song)) {
						            	playlist.addSong(song);
				            		}
					            }
				            }
				            break;
			            case "Album":
			            	Album album = (Album) MusicPlayer.getDraggedItem();
				            for (Song song : album.getSongs()) {
				            	if (!playlist.getSongs().contains(song)) {
					            	playlist.addSong(song);
			            		}
				            }
				            break;
			            case "Playlist":
			            	Playlist list = (Playlist) MusicPlayer.getDraggedItem();
				            for (Song song : list.getSongs()) {
				            	if (!playlist.getSongs().contains(song)) {
					            	playlist.addSong(song);
			            		}
				            }
				            break;
			            case "Song":
			            	Song song = (Song) MusicPlayer.getDraggedItem();
			            	if (!playlist.getSongs().contains(song)) {
				            	playlist.addSong(song);
		            		}
				            break;
			            case "List":
							ObservableList<Song> songs = (ObservableList<Song>) MusicPlayer.getDraggedItem();
			            	for (Song s : songs) {
			            		if (!playlist.getSongs().contains(s)) {
					            	playlist.addSong(s);
			            		}
			            	}
			            	break;
			            }
					}).start();

					event.consume();
				});

    			cell.setPrefHeight(0);
    			cell.setOpacity(0);

    			playlistBox.getChildren().add(1, cell);

    			textBox.requestFocus();

    		} catch (Exception e) {

    			e.printStackTrace();
    		}

        	newPlaylistAnimation.play();
    	}
    }
    
    private String checkDuplicatePlaylist(String text, int i) {
    	for (Playlist playlist : Library.getPlaylists()) {
    		if (playlist.getTitle().equals(text)) {

    			int index = text.lastIndexOf(' ') + 1;
    			if (index != 0) {
    				try {
    					i = Integer.parseInt(text.substring(index));
    				} catch (Exception ex) {
    					// do nothing
    				}
    			}

    			i++;

    			if (i == 1) {
    				text = checkDuplicatePlaylist(text + " " + i, i);
    			} else {
    				text = checkDuplicatePlaylist(text.substring(0, index) + i, i);
    			}
    			break;
    		}
    	}

    	return text;
    }
    
    public SubView loadView(String viewName) {
        try {

        	boolean loadLetters;
        	boolean unloadLetters;

        	switch (viewName.toLowerCase()) {
        	case "artists":
        	case "artistsmain":
        	case "albums":
        	case "songs":
        		if (subViewController instanceof ArtistsController
        			|| subViewController instanceof ArtistsMainController
        			|| subViewController instanceof AlbumsController
        			|| subViewController instanceof SongsController
				    || subViewController instanceof StreamingController) {
        			loadLetters = false;
        			unloadLetters = false;
        		} else {
        			loadLetters = true;
        			unloadLetters = false;
        		}
        		break;
        	default:
        		if (subViewController instanceof ArtistsController
        			|| subViewController instanceof ArtistsMainController
        			|| subViewController instanceof AlbumsController
        			|| subViewController instanceof SongsController
					|| subViewController instanceof StreamingController) {
        			loadLetters = false;
        			unloadLetters = true;
        		} else {
        			loadLetters = false;
        			unloadLetters = false;
        		}
        		break;
        	}

	        final boolean loadLettersFinal = loadLetters;
	        final boolean unloadLettersFinal = unloadLetters;

            String fileName = viewName.substring(0, 1).toUpperCase() + viewName.substring(1) + ".fxml";
            
            FXMLLoader loader = new FXMLLoader(this.getClass().getResource(fileName));
            Node view = loader.load();
            
            CountDownLatch latch = new CountDownLatch(1);
            
            Task<Void> task = new Task<Void>() {
	        	@Override protected Void call() throws Exception {
	        		Platform.runLater(() -> {
	        			Library.getSongs().stream().filter(x -> x.getSelected()).forEach(x -> x.setSelected(false));
	        			subViewRoot.setVisible(false);
			        	subViewRoot.setContent(view);
			        	subViewRoot.getContent().setOpacity(0);
			        	latch.countDown();
	        		});
		        	return null;
	        	}
	        };

	        task.setOnSucceeded(x -> new Thread(() -> {
                try {
                    latch.await();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> {
                    subViewRoot.setVisible(true);
                    if (loadLettersFinal) {
                        loadLettersAnimation.play();
                    }
                    loadViewAnimation.play();
                });
            }).start());

	        Thread thread = new Thread(task);
            
            unloadViewAnimation.setOnFinished(x -> thread.start());
            
            loadViewAnimation.setOnFinished(x -> viewLoadedLatch.countDown());
            
            if (subViewRoot.getContent() != null) {
            	if (unloadLettersFinal) {
            		unloadLettersAnimation.play();
            	}
	            unloadViewAnimation.play();
        	} else {
        		subViewRoot.setContent(view);
        		if (loadLettersFinal) {
        			loadLettersAnimation.play();
        		}
        		loadViewAnimation.play();
        	}
            
            subViewController = loader.getController();
            return subViewController;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
    
    @FXML
    private void navigateToCurrentSong() {

    	Optional<Node> previous = sideBar.getChildren().stream()
                .filter(x -> x.getStyleClass().get(0).equals("sideBarItemSelected")).findFirst();

        if (previous.isPresent()) {
            HBox previousItem = (HBox) previous.get();
            previousItem.getStyleClass().setAll("sideBarItem");
        } else {
        	previous = playlistBox.getChildren().stream()
                    .filter(x -> x.getStyleClass().get(0).equals("sideBarItemSelected")).findFirst();
        	if (previous.isPresent()) {
                HBox previousItem = (HBox) previous.get();
                previousItem.getStyleClass().setAll("sideBarItem");
            }
        }
        
        sideBar.getChildren().get(2).getStyleClass().setAll("sideBarItemSelected");
        
        ArtistsMainController artistsMainController = (ArtistsMainController) loadView("ArtistsMain");
        Song song = MusicPlayer.getNowPlaying();
        Artist artist = Library.getArtist(song.getArtist());
        Album album = artist.getAlbums().stream().filter(x -> x.getTitle().equals(song.getAlbum())).findFirst().get();
        artistsMainController.selectArtist(artist);
        artistsMainController.selectAlbum(album);
        artistsMainController.selectSong(song);
    }

    @FXML
    private void slideSideBar(Event e) {
    	sideBar.requestFocus();
    	searchBox.setText("");
        if (isSideBarExpanded) {
            collapseSideBar();
        } else {
            expandSideBar();
        }
    }
    
    private void collapseSideBar() {
        if (expandAnimation.statusProperty().get() == Animation.Status.STOPPED
            && collapseAnimation.statusProperty().get() == Animation.Status.STOPPED) {

            collapseAnimation.play();
        }
    }

    private void expandSideBar() {
        if (expandAnimation.statusProperty().get() == Animation.Status.STOPPED
            && collapseAnimation.statusProperty().get() == Animation.Status.STOPPED) {

        	expandAnimation.play();
        }
    }

    @FXML
    public void playPause() {

    	sideBar.requestFocus();

        if (MusicPlayer.isPlaying()) {
            MusicPlayer.pause();
        } else {
            MusicPlayer.play();
        }
    }

    @FXML
    private void back() {

    	sideBar.requestFocus();
        MusicPlayer.back();
    }

    @FXML
    private void skip() {

    	sideBar.requestFocus();
        MusicPlayer.skip();
    }
    
    @FXML
    private void letterClicked(Event e) {

    	sideBar.requestFocus();
    	Label eventSource = ((Label)e.getSource());
    	char letter = eventSource.getText().charAt(0);
    	subViewController.scroll(letter);
    }
    
    public void volumeClick() {
    	if (!volumePopup.isShowing()) {
    		Stage stage = MusicPlayer.getStage();
    		volumePopup.setX(stage.getX() + stage.getWidth() - 265);
        	volumePopup.setY(stage.getY() + stage.getHeight() - 115);
    		volumePopup.show();
    		volumeShowAnimation.play();
    	}
    }

    public void showSearchResults(SearchResult result) {
        VBox root = (VBox) searchPopup.getScene().getRoot();
        ObservableList<Node> list = root.getChildren();
        list.clear();

		// 记录是否有本地搜索结果
		boolean hasLocalResults = false;

        if (result.getArtistResults().size() > 0) {
			hasLocalResults = true;
            Label header = new Label("Artists");
            list.add(header);
            VBox.setMargin(header, new Insets(10, 10, 10, 10));
            result.getArtistResults().forEach(artist -> {
                HBox cell = new HBox();
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPrefWidth(226);
                cell.setPrefHeight(50);
                ImageView image = new ImageView();
                image.setFitHeight(40);
                image.setFitWidth(40);
                image.setImage(artist.getArtistImage());
                Label label = new Label(artist.getTitle());
                label.setTextOverrun(OverrunStyle.CLIP);
                label.getStyleClass().setAll("searchLabel");
                cell.getChildren().addAll(image, label);
                HBox.setMargin(image, new Insets(5, 5, 5, 5));
                HBox.setMargin(label, new Insets(10, 10, 10, 5));
                cell.getStyleClass().add("searchResult");
                cell.setOnMouseClicked(event -> {
                    loadView("ArtistsMain");
                    ArtistsMainController artistsMainController = (ArtistsMainController) loadView("ArtistsMain");
                    artistsMainController.selectArtist(artist);
                    searchBox.setText("");
                    sideBar.requestFocus();
                });
                list.add(cell);
            });
            Separator separator = new Separator();
            separator.setPrefWidth(206);
            list.add(separator);
            VBox.setMargin(separator, new Insets(10, 10, 0, 10));
        }
        if (result.getAlbumResults().size() > 0) {
			hasLocalResults = true;
            Label header = new Label("Albums");
            list.add(header);
            VBox.setMargin(header, new Insets(10, 10, 10, 10));
            result.getAlbumResults().forEach(album -> {
                HBox cell = new HBox();
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPrefWidth(226);
                cell.setPrefHeight(50);
                ImageView image = new ImageView();
                image.setFitHeight(40);
                image.setFitWidth(40);
                image.setImage(album.getArtwork());
                Label label = new Label(album.getTitle());
                label.setTextOverrun(OverrunStyle.CLIP);
                label.getStyleClass().setAll("searchLabel");
                cell.getChildren().addAll(image, label);
                HBox.setMargin(image, new Insets(5, 5, 5, 5));
                HBox.setMargin(label, new Insets(10, 10, 10, 5));
                cell.getStyleClass().add("searchResult");
                cell.setOnMouseClicked(event -> {
                    loadView("ArtistsMain");
                    Artist artist = Library.getArtist(album.getArtist());
                    ArtistsMainController artistsMainController = (ArtistsMainController) loadView("ArtistsMain");
                    artistsMainController.selectArtist(artist);
                    artistsMainController.selectAlbum(album);
                    searchBox.setText("");
                    sideBar.requestFocus();
                });
                list.add(cell);
            });
            Separator separator = new Separator();
            separator.setPrefWidth(206);
            list.add(separator);
            VBox.setMargin(separator, new Insets(10, 10, 0, 10));
        }
        if (result.getSongResults().size() > 0) {
			hasLocalResults = true;
            Label header = new Label("Songs");
            list.add(header);
            VBox.setMargin(header, new Insets(10, 10, 10, 10));
            result.getSongResults().forEach(song -> {
                HBox cell = new HBox();
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setPrefWidth(226);
                cell.setPrefHeight(50);
                Label label = new Label(song.getTitle());
                label.setTextOverrun(OverrunStyle.CLIP);
                label.getStyleClass().setAll("searchLabel");
                cell.getChildren().add(label);
                HBox.setMargin(label, new Insets(10, 10, 10, 10));
                cell.getStyleClass().add("searchResult");
                cell.setOnMouseClicked(event -> {
                    loadView("ArtistsMain");
                    Artist artist = Library.getArtist(song.getArtist());
                    Album album = artist.getAlbums().stream().filter(x -> x.getTitle().equals(song.getAlbum())).findFirst().get();
                    ArtistsMainController artistsMainController = (ArtistsMainController) loadView("ArtistsMain");
                    artistsMainController.selectArtist(artist);
                    artistsMainController.selectAlbum(album);
                    artistsMainController.selectSong(song);
                    searchBox.setText("");
                    sideBar.requestFocus();
                });
                list.add(cell);
            });
        }
		if (!hasLocalResults) {
			Label label = new Label("No Results");
			list.add(label);
			VBox.setMargin(label, new Insets(10, 10, 10, 10));
		}

		// 在原有搜索结果下方添加固定文本控件
		if (hasLocalResults) {
			Separator bottomSeparator = new Separator();
			bottomSeparator.setPrefWidth(206);
			list.add(bottomSeparator);
			VBox.setMargin(bottomSeparator, new Insets(10, 10, 0, 10));
		}

		// 网络搜索控件
		HBox moreBox = new HBox();
		moreBox.setAlignment(Pos.CENTER_LEFT);
		moreBox.setPrefWidth(226);
		moreBox.setPrefHeight(50);
		Label moreLabel = new Label("Search online for:\""+searchBox.getText()+"\"");//转义符，显示用户搜索的关键词
		moreLabel.setTextOverrun(OverrunStyle.CLIP);
		moreLabel.getStyleClass().setAll("searchLabel");
		moreBox.getChildren().add(moreLabel);
		HBox.setMargin(moreLabel, new Insets(10, 10, 10, 10));
		moreBox.getStyleClass().add("searchResult");
		moreBox.setOnMouseClicked(event -> {
			String searchTerm = searchBox.getText().trim();
			if (!searchTerm.isEmpty()) {
				// 创建并执行搜索任务
				ApiSearchTask searchTask = new ApiSearchTask(
						searchTerm,
						() -> showNotification("API搜索完成", "结果已保存到本地"),
						() -> showNotification("API搜索失败", getExceptionMessage())
				);

				new Thread(searchTask).start();
			}
			searchBox.setText("");
			searchHideAnimation.play();
			sideBar.requestFocus();
		});
		list.add(moreBox);

        if (!searchPopup.isShowing()) {
            Stage stage = MusicPlayer.getStage();
            searchPopup.setX(stage.getX() + 18);
            searchPopup.setY(stage.getY() + 80);
            searchPopup.show();
            searchShowAnimation.play();
        }
    }

	// 添加辅助方法获取异常信息
	private String getExceptionMessage() {
		Throwable ex = (Throwable) Thread.currentThread().getUncaughtExceptionHandler();
		return ex != null ? ex.getMessage() : "未知错误";
	}

	private void showNotification(String title, String message) {
		Platform.runLater(() -> {
			Alert alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setTitle(title);
			alert.setHeaderText(null);
			alert.setContentText(message);
			alert.initOwner(MusicPlayer.getStage());

			// 添加自定义按钮
			ButtonType viewResultsButton = new ButtonType("查看结果", ButtonBar.ButtonData.OK_DONE);
			ButtonType closeButton = new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE);
			alert.getButtonTypes().setAll(viewResultsButton, closeButton);

			Optional<ButtonType> result = alert.showAndWait();
			if (result.isPresent() && result.get() == viewResultsButton) {
				// 找到流媒体视图的侧边栏项并触发点击事件
				switchToStreamingView();
			}
		});
	}

	private void switchToStreamingView() {
		// 找到流媒体视图的侧边栏项
		HBox streamingItem = null;

		// 在侧边栏中查找
		for (Node node : sideBar.getChildren()) {
			if (node instanceof HBox && "Streaming".equals(node.getId())) {
				streamingItem = (HBox) node;
				break;
			}
		}

		// 如果没找到，在播放列表框中查找
		if (streamingItem == null) {
			for (Node node : playlistBox.getChildren()) {
				if (node instanceof HBox && "streaming".equals(node.getId())) {
					streamingItem = (HBox) node;
					break;
				}
			}
		}

		// 如果找到，模拟点击事件
		if (streamingItem != null) {
			// 创建模拟点击事件
			MouseEvent clickEvent = new MouseEvent(
					MouseEvent.MOUSE_CLICKED,
					0, 0, 0, 0,
					MouseButton.PRIMARY, 1,
					false, false, false, false,
					true, false, false, false,
					false, false,
					null
			);

			// 触发点击事件
			streamingItem.fireEvent(clickEvent);
		} else {
			showNotification("错误", "无法找到流媒体视图选项");
		}
	}
    
    public Slider getVolumeSlider() {
    	return volumePopupController.getSlider();
    }
    
    public boolean isTimeSliderPressed() {
    	return sliderSkin.getThumb().isPressed() || sliderSkin.getTrack().isPressed();
    }
    
    public SubView getSubViewController() {

    	return subViewController;
    }
    
    ScrollPane getScrollPane() {
    	return this.subViewRoot;
    }

    VBox getPlaylistBox() {
    	return playlistBox;
    }

	public void updateLoopButtons() {
		PseudoClass active = PseudoClass.getPseudoClass("active");
		loopButton.pseudoClassStateChanged(active, MusicPlayer.isLoopActive());
		repeatButton.pseudoClassStateChanged(active, MusicPlayer.isRepeatActive());

		// 确保两种循环模式不会同时激活
		if (MusicPlayer.isRepeatActive()) {
			loopButton.pseudoClassStateChanged(active, false);
		}
	}

    public void updatePlayPauseIcon(boolean isPlaying) {

    	controlBox.getChildren().remove(1);
    	if (isPlaying) {
           	controlBox.getChildren().add(1, pauseButton);
        } else {
          	controlBox.getChildren().add(1, playButton);
        }
    }

    private void setSlideDirection() {
        isSideBarExpanded = !isSideBarExpanded;
    }
    
    private Animation volumeShowAnimation = new Transition() {
    	{
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }

        protected void interpolate(double frac) {
        	volumePopup.setOpacity(frac);
        }
    };
    
    private Animation volumeHideAnimation = new Transition() {
    	{
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }
        protected void interpolate(double frac) {
            volumePopup.setOpacity(1.0 - frac);
        }
    };

    private Animation searchShowAnimation = new Transition() {
        {
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }

        protected void interpolate(double frac) {
            searchPopup.setOpacity(frac);
        }
    };

    private Animation searchHideAnimation = new Transition() {
        {
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }
        protected void interpolate(double frac) {
            searchPopup.setOpacity(1.0 - frac);
        }
    };
    
    private Animation collapseAnimation = new Transition() {
        {
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
            setOnFinished(x -> setSlideDirection());
        }
        protected void interpolate(double frac) {
            double curWidth = collapsedWidth + (expandedWidth - collapsedWidth) * (1.0 - frac);
			double searchWidth = searchCollapsed + (searchExpanded - searchCollapsed) * (1.0 - frac);
            sideBar.setPrefWidth(curWidth);
			searchBox.setPrefWidth(searchWidth);
			searchBox.setOpacity(1.0 - frac);
        }
    };

    private Animation expandAnimation = new Transition() {
        {
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
            setOnFinished(x -> setSlideDirection());
        }
        protected void interpolate(double frac) {
            double curWidth = collapsedWidth + (expandedWidth - collapsedWidth) * (frac);
			double searchWidth = searchCollapsed + (searchExpanded - searchCollapsed) * (frac);
			sideBar.setPrefWidth(curWidth);
			searchBox.setPrefWidth(searchWidth);
			searchBox.setOpacity(frac);
        }
    };

    private Animation loadViewAnimation = new Transition() {
        {
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }
        protected void interpolate(double frac) {
            subViewRoot.setVvalue(0);
            double curHeight = collapsedHeight + (expandedHeight - collapsedHeight) * (frac);
            subViewRoot.getContent().setTranslateY(expandedHeight - curHeight);
            subViewRoot.getContent().setOpacity(frac);
        }
    };
    
    private Animation unloadViewAnimation = new Transition() {
        {
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }
        protected void interpolate(double frac) {
            double curHeight = collapsedHeight + (expandedHeight - collapsedHeight) * (1 - frac);
            subViewRoot.getContent().setTranslateY(expandedHeight - curHeight);
            subViewRoot.getContent().setOpacity(1 - frac);
        }
    };
    
    private Animation loadLettersAnimation = new Transition() {
    	{
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }
        protected void interpolate(double frac) {
        	letterBox.setPrefHeight(50);
    		letterBox.setOpacity(frac);
    		letterSeparator.setPrefHeight(25);
    		letterSeparator.setOpacity(frac);
        }
    };
    
    private Animation unloadLettersAnimation = new Transition() {
    	{
            setCycleDuration(Duration.millis(250));
            setInterpolator(Interpolator.EASE_BOTH);
        }
        protected void interpolate(double frac) {
    		letterBox.setOpacity(1.0 - frac);
    		letterSeparator.setOpacity(1.0 - frac);
        }
    };
    
    private Animation newPlaylistAnimation = new Transition() {
    	{
            setCycleDuration(Duration.millis(500));
            setInterpolator(Interpolator.EASE_BOTH);
        }
        protected void interpolate(double frac) {
    		HBox cell = (HBox) playlistBox.getChildren().get(1);
    		if (frac < 0.5) {
    			cell.setPrefHeight(frac * 100);
    		} else {
    			cell.setPrefHeight(50);
    			cell.setOpacity((frac - 0.5) * 2);
    		}
        }
    };
}
