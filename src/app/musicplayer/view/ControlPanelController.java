package app.musicplayer.view;

import java.net.URL;
import java.util.ResourceBundle;

import app.musicplayer.MusicPlayer;
import app.musicplayer.model.Library;
import app.musicplayer.model.Playlist;
import app.musicplayer.model.Song;
import app.musicplayer.util.SubView;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

public class ControlPanelController implements Initializable {

	// 播放按钮
	@FXML private Pane playButton;
	// 播放列表按钮
	@FXML private Pane playlistButton;

	// 上下文菜单
	private ContextMenu contextMenu;

	// 显示菜单动画
	private Animation showMenuAnimation = new Transition() {
		{
			// 设置动画持续时间为250毫秒
			setCycleDuration(Duration.millis(250));
			// 设置动画插值为EaseBoth
			setInterpolator(Interpolator.EASE_BOTH);
		}
		protected void interpolate(double frac) {
			// 设置上下文菜单的透明度为动画进度
			contextMenu.setOpacity(frac);
		}
	};

	// 初始化方法
	@Override
	public void initialize(URL location, ResourceBundle resources) {}

	// 播放歌曲方法
	@FXML
	private void playSong(Event e) {
		// 获取主控制器的子视图控制器
		SubView controller = MusicPlayer.getMainController().getSubViewController();
		// 播放歌曲
		controller.play();
		// 消费事件
		e.consume();
	}

	// 添加到播放列表方法
	@FXML
	private void addToPlaylist(Event e) {
		// 获取鼠标事件在屏幕上的坐标，以便在指定位置显示上下文菜单。
		MouseEvent mouseEvent = (MouseEvent) e;
		double x = mouseEvent.getScreenX();
		double y = mouseEvent.getScreenY();

		// 获取要添加到播放列表的歌曲。
		Song selectedSong = MusicPlayer.getMainController().getSubViewController().getSelectedSong();

		ObservableList<Playlist> playlists = Library.getPlaylists();

		// 获取所有播放列表标题以创建菜单项。
		ObservableList<String> playlistTitles = FXCollections.observableArrayList();
		for (Playlist playlist : playlists) {
			String title = playlist.getTitle();
			if (!(title.equals("Most Played") || title.equals("Recently Played")) &&
					!playlist.getSongs().contains(selectedSong)) {
				playlistTitles.add(title);
			}
		}

		contextMenu = new ContextMenu();

		MenuItem playing = new MenuItem("正在播放");
		playing.setStyle("-fx-text-fill: black");
		playing.setOnAction(e1 -> {
			MusicPlayer.addSongToNowPlayingList(selectedSong);
		});

		contextMenu.getItems().add(playing);

		if (playlistTitles.size() > 0) {
			SeparatorMenuItem item = new SeparatorMenuItem();
			item.getContent().setStyle(
					"-fx-border-width: 1 0 0 0; " +
							"-fx-border-color: #c2c2c2; " +
							"-fx-border-insets: 5 5 5 5;");
			contextMenu.getItems().add(item);
		}

		// 为每个播放列表标题创建菜单项并添加到上下文菜单中。
		for (String title : playlistTitles) {
			MenuItem item = new MenuItem(title);
			item.setStyle("-fx-text-fill: black");

			item.setOnAction(e2 -> {
				// 找到目标播放列表并将当前选中的歌曲添加到其中。
				String targetPlaylistTitle = item.getText();

				// 找到正确的播放列表并将歌曲添加到其中。
				playlists.forEach(playlist -> {
					if (playlist.getTitle().equals(targetPlaylistTitle)) {
						playlist.addSong(selectedSong);
					}
				});
			});

			contextMenu.getItems().add(item);
		}

		contextMenu.setOpacity(0);
		contextMenu.show(playButton, x, y);
		showMenuAnimation.play();

		e.consume();
	}
}
