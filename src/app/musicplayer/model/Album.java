package app.musicplayer.model;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import app.musicplayer.util.Resources;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;

public final class Album implements Comparable<Album> {

    private int id;
    private String title;
    private String artist;
    private Image artwork;
    private ArrayList<Song> songs;
    private SimpleObjectProperty<Image> artworkProperty;

    /**
     * Constructor for the Album class. 
     * Creates an album object and obtains the album artwork.
     *
     * @param id
     * @param title
     * @param artist
     * @param songs
     */
    public Album(int id, String title, String artist, ArrayList<Song> songs) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.songs = songs;
        this.artworkProperty = new SimpleObjectProperty<>(getArtwork());
    }

    /**
     * Gets album ID.
     *
     * @return album ID
     */
    public int getId() {
        return this.id;
    }

    /**
     * Gets album title
     *
     * @return album title
     */
    public String getTitle() {
        return this.title;
    }

    public String getArtist() {
        return this.artist;
    }

    public ArrayList<Song> getSongs() {
        return new ArrayList<>(this.songs);
    }

    public ObjectProperty<Image> artworkProperty() {
        return this.artworkProperty;
    }

    public Image getArtwork() {
        if (this.artwork == null) {

            try {
                String location = this.songs.get(0).getLocation();
                AudioFile audioFile = AudioFileIO.read(new File(location));
                Tag tag = audioFile.getTag();
                byte[] bytes = tag.getFirstArtwork().getBinaryData();
                ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                this.artwork = new Image(in, 300, 300, true, true);

                if (this.artwork.isError()) {
                    this.artwork = new Image(Resources.IMG + "albumsIcon.png");
                }

            } catch (Exception ex) {
                this.artwork = new Image(Resources.IMG + "albumsIcon.png");
            }
        }
        return this.artwork;
    }

    public void downloadArtwork() {
        try {
            // 创建XMLInputFactory实例
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // 创建URL对象，用于获取XML数据
            URL xmlData = new URL(Resources.APIBASE
                    + "method=album.getinfo"
                    + "&api_key=" + Resources.APIKEY
                    + "&artist=" + URLEncoder.encode(this.artist, "UTF-8")
                    + "&album=" + URLEncoder.encode(this.title, "UTF-8")
                    );

            // 创建XMLStreamReader对象，用于读取XML数据
            XMLStreamReader reader = factory.createXMLStreamReader(xmlData.openStream(), "UTF-8");

            // 循环读取XML数据
            while (reader.hasNext()) {

                reader.next();

                // 如果是开始标签，并且标签名为image，并且属性值为extralarge
                if (reader.isStartElement()
                        && reader.getName().getLocalPart().equals("image")
                        && reader.getAttributeValue(0).equals("extralarge")) {

                    reader.next();

                    // 如果有文本内容
                    if (reader.hasText()) {
                        // 读取图片数据
                        BufferedImage bufferedImage = ImageIO.read(new URL(reader.getText()));
                        // 创建新的BufferedImage对象，用于存储图片数据
                        BufferedImage newBufferedImage = new BufferedImage(bufferedImage.getWidth(),
                                bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                        // 将图片数据绘制到新的BufferedImage对象上
                        newBufferedImage.createGraphics().drawImage(bufferedImage, 0, 0, Color.WHITE, null);
                        // 创建临时文件，用于存储图片数据
                        File file = File.createTempFile("temp", "temp");
                        // 将图片数据写入临时文件
                        ImageIO.write(newBufferedImage, "jpg", file);

                        // 遍历歌曲列表
                        for (Song song : this.songs) {

                            // 读取音频文件
                            AudioFile audioFile = AudioFileIO.read(new File(song.getLocation()));
                            // 获取音频文件的标签
                            Tag tag = audioFile.getTag();
                            // 删除音频文件的封面图片
                            tag.deleteArtworkField();

                            // 创建封面图片对象
                            Artwork artwork = ArtworkFactory.createArtworkFromFile(file);
                            // 将封面图片添加到音频文件的标签中
                            tag.setField(artwork);
                            // 将音频文件写入磁盘
                            AudioFileIO.write(audioFile);
                        }

                        // 删除临时文件
                        file.delete();
                    }
                }
            }
            // 获取第一首歌曲的路径
            String location = this.songs.get(0).getLocation();
            // 读取音频文件
            AudioFile audioFile = AudioFileIO.read(new File(location));
            // 获取音频文件的标签
            Tag tag = audioFile.getTag();
            // 获取音频文件的封面图片数据
            byte[] bytes = tag.getFirstArtwork().getBinaryData();
            // 创建ByteArrayInputStream对象，用于读取封面图片数据
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            // 将封面图片数据转换为Image对象
            this.artwork = new Image(in, 300, 300, true, true);

            // 如果转换失败
            if (this.artwork.isError()) {

                // 使用默认的封面图片
                this.artwork = new Image(Resources.IMG + "albumsIcon.png");
            }

            // 设置封面图片属性
            this.artworkProperty.setValue(artwork);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public int compareTo(Album other) {
        String first = removeArticle(this.title);
        String second = removeArticle(other.title);

        return first.compareTo(second);
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
}
