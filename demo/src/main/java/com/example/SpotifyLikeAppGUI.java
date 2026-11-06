package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.*;
import javax.swing.*;

public class SpotifyLikeAppGUI {

  // One song's info, exactly as it is saved in track-database.json
  static class Track {
    String title;
    String artist;
    int year;
    String genre;
    @SerializedName("IsFavorite") // saved as "IsFavorite", spelled like the assignment
    boolean isFavorite;
    String comments;
    String filePath; // relative path to the audio file in the git repo
  }

  // folder that holds audio-library.json and the wav folder
  private static String directoryPath =
    "/Users/henryquach/Documents/GitHub/spotify-like-app/demo/src/main/java/com/example";

  // the same folder, written relative to the root of the git repository
  private static final String REPO_FOLDER = "demo/src/main/java/com/example";

    // the file where comments and favorites are saved
  private static final String DATABASE_FILE = "track-database.json";

  // star symbol shown next to favorite songs
  private static final String STAR = "★";

  // all the songs in the app
  private static List<Track> tracks = new ArrayList<>();

  // the current audio clip and the song it belongs to
  private static Clip audioClip;
  private static Track nowPlaying;

  // parts of the window we need to update later
  private static JFrame frame;
  private static JPanel screens; // holds the Home and Library screens
  private static JTextArea welcomeArea;
  private static DefaultListModel<String> listModel;
  private static JList<String> songList;
  private static JLabel nowPlayingLabel;
  private static JTextArea infoArea;
  private static JTextArea commentBox;
  private static JCheckBox favoriteBox;
  private static JLabel statusLabel;

  // "main" makes this class a java app that can be executed
  public static void main(String[] args) {
    findDirectory();
    loadTracks();

    // Swing windows should be built on the Swing thread
    SwingUtilities.invokeLater(() -> buildWindow());
  }

  // ================================================================
  //  LOADING SONGS
  // ================================================================

  /*
   * Finds the folder with audio-library.json. It tries the full path
   * first, then relative paths, so the app works whether VS Code runs
   * it from the repo folder or from the demo folder.
   */
  private static void findDirectory() {
    String[] places = { directoryPath, REPO_FOLDER, "src/main/java/com/example" };

    for (String place : places) {
      if (new File(place, "audio-library.json").exists()) {
        directoryPath = place;
        return;
      }
    }
    System.out.println("ERROR: could not find audio-library.json");
  }

    /*
   * Reads the songs from audio-library.json, then adds the saved
   * comments and favorites from track-database.json (if it exists).
   */
  private static void loadTracks() {
    // 1. read the original library (same as the console app)
    Song[] library = readJson(new File(directoryPath, "audio-library.json"), Song[].class);
    if (library == null) {
      return; // nothing to load
    }

    // 2. read the saved user settings, if we saved them before
    Map<String, Track> saved = new HashMap<>();
    File databaseFile = new File(directoryPath, DATABASE_FILE);
    if (databaseFile.exists()) {
      Track[] savedTracks = readJson(databaseFile, Track[].class);
      if (savedTracks != null) {
        for (Track t : savedTracks) {
          saved.put(t.title, t);
        }
      }
    }

    // 3. combine them into one list of tracks
    for (Song song : library) {
      Track t = new Track();
      t.title = song.name();
      t.artist = song.artist();
      t.year = song.year();
      t.genre = song.genre();
      t.filePath = REPO_FOLDER + "/wav/" + song.fileName();
      t.comments = "";

      // if we saved settings for this song before, load them back
      Track old = saved.get(t.title);
      if (old != null) {
        t.isFavorite = old.isFavorite;
        if (old.comments != null) {
          t.comments = old.comments;
        }
      }

      tracks.add(t);
    }

    // make sure the database file exists right away
    saveDatabase();
  }

  // saves every track (with comments and favorites) to track-database.json
  private static void saveDatabase() {
    // never replace the saved settings with an empty list
    if (tracks.isEmpty()) {
      return;
    }
    File databaseFile = new File(directoryPath, DATABASE_FILE);
    try (FileWriter writer = new FileWriter(databaseFile, StandardCharsets.UTF_8)) {
      Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
      gson.toJson(tracks, writer);
      setStatus("Saved to " + DATABASE_FILE);
    } catch (Exception e) {
      setStatus("ERROR: could not save " + DATABASE_FILE);
    }
  }

  // reads a JSON file into an array (like Song[]); returns null if it fails
  private static <T> T readJson(File file, Class<T> type) {
    try (JsonReader reader = new JsonReader(new FileReader(file, StandardCharsets.UTF_8))) {
      return new Gson().fromJson(reader, type);
    } catch (Exception e) {
      System.out.println("ERROR: unable to read " + file);
      return null;
    }
  }

  // ================================================================
  //  THE WINDOW
  // ================================================================

  private static void buildWindow() {
    frame = new JFrame("SpotifyLikeApp");
    frame.setLayout(new BorderLayout(10, 10));

    // ---- top: the button rows ----
    JPanel menuRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    menuRow.add(new JLabel("Menu:"));
    menuRow.add(makeButton("Home", () -> home()));
    menuRow.add(makeButton("Search", () -> search()));
    menuRow.add(makeButton("Library", () -> library()));
    menuRow.add(makeButton("Quit", () -> quit()));

    JPanel top = new JPanel(new GridLayout(0, 1)); // one row of buttons per line
    top.add(menuRow);
    JPanel playerRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
    playerRow.add(new JLabel("Player:"));
    playerRow.add(makeButton("Play", () -> play()));
    playerRow.add(makeButton("Pause", () -> pause()));
    playerRow.add(makeButton("Stop", () -> stop()));
    playerRow.add(makeButton("Rewind 5s", () -> rewind()));
    top.add(playerRow);
    frame.add(top, BorderLayout.NORTH);

    // ---- Home screen: just the welcome message ----
    welcomeArea = new JTextArea();
    welcomeArea.setEditable(false);
    welcomeArea.setMargin(new Insets(10, 10, 10, 10));
    JScrollPane homeScreen = new JScrollPane(welcomeArea);
    homeScreen.setBorder(BorderFactory.createTitledBorder("Home"));

    // ---- Library screen, left side: the song list ----
    listModel = new DefaultListModel<>();
    songList = new JList<>(listModel);
    songList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane listScroll = new JScrollPane(songList);
    listScroll.setBorder(BorderFactory.createTitledBorder("Songs"));

    // ---- Library screen, right side: "Now Playing" line + song info ----
    nowPlayingLabel = new JLabel("Now Playing: nothing yet");
    nowPlayingLabel.setFont(nowPlayingLabel.getFont().deriveFont(Font.BOLD));

    infoArea = new JTextArea();
    infoArea.setEditable(false);
    infoArea.setLineWrap(true);
    infoArea.setWrapStyleWord(true);
    JScrollPane infoScroll = new JScrollPane(infoArea);
    infoScroll.setBorder(BorderFactory.createTitledBorder("Song Info"));

    JPanel right = new JPanel(new BorderLayout(0, 10));
    right.add(nowPlayingLabel, BorderLayout.NORTH);

    // under the song info: comment box + Favorite checkbox
    commentBox = new JTextArea(4, 20);
    commentBox.setLineWrap(true);
    commentBox.setWrapStyleWord(true);
    JScrollPane commentScroll = new JScrollPane(commentBox);

    favoriteBox = new JCheckBox("Favorite");
    favoriteBox.addActionListener(e -> toggleFavorite());

    JPanel commentButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    commentButtons.add(favoriteBox);
    commentButtons.add(makeButton("Save Comment", () -> saveComment()));

    JPanel commentPanel = new JPanel(new BorderLayout());
    commentPanel.setBorder(BorderFactory.createTitledBorder("Your comments on this song"));
    commentPanel.add(commentScroll, BorderLayout.CENTER);
    commentPanel.add(commentButtons, BorderLayout.SOUTH);

    JPanel details = new JPanel(new GridLayout(2, 1, 0, 10));
    details.add(infoScroll);
    details.add(commentPanel);
    right.add(details, BorderLayout.CENTER);

    JPanel libraryScreen = new JPanel(new GridLayout(1, 2, 10, 0));
    libraryScreen.add(listScroll);
    libraryScreen.add(right);

    // when you click a song, show its info
    songList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        showSelectedTrack();
        showSelectedSettings();
      }
    });

    // ---- the middle of the window shows one screen at a time ----
    screens = new JPanel(new CardLayout());
    screens.add(homeScreen, "home");
    screens.add(libraryScreen, "library");
    frame.add(screens, BorderLayout.CENTER);

    // ---- bottom: status line ----
    statusLabel = new JLabel(" ");
    frame.add(statusLabel, BorderLayout.SOUTH);

    // closing the window works like the Quit button
    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        quit();
      }
    });

    refreshList();
    showSelectedTrack();
    home();

    frame.setSize(820, 520);
    frame.setLocationRelativeTo(null); // center on screen
    frame.setVisible(true);
  }

  // creates a button that runs "action" when clicked
  private static JButton makeButton(String text, Runnable action) {
    JButton button = new JButton(text);
    button.addActionListener(e -> action.run());
    return button;
  }

  // switches the middle of the window to "home" or "library"
  private static void showScreen(String name) {
    ((CardLayout) screens.getLayout()).show(screens, name);
  }

  // ================================================================
  //  MENU BUTTONS
  // ================================================================

  private static void home() {
    int favorites = 0;
    for (Track t : tracks) {
      if (t.isFavorite) {
        favorites++;
      }
    }
    welcomeArea.setText(
      "Welcome to the SpotifyLikeApp!\n\n" +
      "You have " + tracks.size() + " songs in your library.\n" +
      "You have " + favorites + " favorite songs.\n\n" +
      "Go to Library, pick a song, and press Play."
    );
    showScreen("home");
    setStatus("Home");
  }

  private static void search() {
    String title = JOptionPane.showInputDialog(frame, "Enter a song title:");
    if (title == null || title.trim().isEmpty()) {
      return; // user pressed Cancel or typed nothing
    }

    String wanted = title.trim().toLowerCase();

    // look for an exact title first, then for a title that contains the text
    int match = -1;
    for (int i = 0; i < tracks.size() && match == -1; i++) {
      if (tracks.get(i).title.toLowerCase().equals(wanted)) {
        match = i;
      }
    }
    for (int i = 0; i < tracks.size() && match == -1; i++) {
      if (tracks.get(i).title.toLowerCase().contains(wanted)) {
        match = i;
      }
    }

    if (match == -1) {
      JOptionPane.showMessageDialog(frame, "Song not found.");
      return;
    }
    showScreen("library");
    songList.setSelectedIndex(match);
    songList.ensureIndexIsVisible(match);
    playTrack(tracks.get(match));
  }

  private static void library() {
    showScreen("library");
    setStatus("Library");
  }

  private static void quit() {
    if (audioClip != null) {
      audioClip.close();
    }
    saveDatabase();
    System.exit(0);
  }

  // ================================================================
  //  PLAYER BUTTONS
  // ================================================================

  private static void play() {
    Track selected = requireSelectedTrack();
    if (selected == null) {
      return;
    }

    boolean paused = (selected == nowPlaying && audioClip != null && !audioClip.isRunning());
    if (paused) {
      audioClip.loop(Clip.LOOP_CONTINUOUSLY); // continue where it left off
      setNowPlaying("");
    } else {
      playTrack(selected);
    }
  }

  private static void pause() {
    if (audioClip != null && audioClip.isRunning()) {
      audioClip.stop(); // stop() keeps the position, so Play resumes
      setNowPlaying(" (paused)");
    }
  }

  private static void stop() {
    if (audioClip != null) {
      audioClip.stop();
      audioClip.setFramePosition(0); // go back to the start
      setNowPlaying(" (stopped)");
    }
  }

  private static void rewind() {
    if (audioClip != null && audioClip.getMicrosecondLength() > 0) {
      // the song loops, so the position can count past the end of the song;
      // % (remainder) turns it back into a spot inside the song
      long position = audioClip.getMicrosecondPosition() % audioClip.getMicrosecondLength();
      long fiveSeconds = 5_000_000; // in microseconds
      audioClip.setMicrosecondPosition(Math.max(position - fiveSeconds, 0));
      setStatus("Rewound 5 seconds");
    }
  }

  // plays an audio file (same idea as play() in the console app)
  private static void playTrack(Track t) {
    // the audio file lives in the wav folder next to audio-library.json
    String fileName = new File(t.filePath).getName();
    File file = new File(directoryPath + "/wav/" + fileName);

    if (!file.exists()) {
      JOptionPane.showMessageDialog(frame, "Can't find the audio file:\n" + file);
      return;
    }

    // stop the current song before playing the next one
    if (audioClip != null) {
      audioClip.close();
      audioClip = null;
      nowPlaying = null;
    }

    try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
      Clip clip = AudioSystem.getClip();
      clip.open(in); // loads the whole song into memory
      clip.loop(Clip.LOOP_CONTINUOUSLY);

      audioClip = clip;
      nowPlaying = t;
      setNowPlaying("");
    } catch (Exception e) {
      JOptionPane.showMessageDialog(frame, "Could not play the song:\n" + e.getMessage());
    }
  }

  // updates the top line, e.g. "Now Playing: Roof - Nctrnm (paused)"
  private static void setNowPlaying(String state) {
    nowPlayingLabel.setText("Now Playing: " + nowPlaying.title + " - " + nowPlaying.artist + state);
  }

  // same as getSelectedTrack, but tells the user to pick a song if none is selected
  private static Track requireSelectedTrack() {
    Track selected = getSelectedTrack();
    if (selected == null) {
      JOptionPane.showMessageDialog(frame, "Pick a song from the library first.");
    }
    return selected;
  }

  // shows the clicked song's info (and its comments) on the right
  private static void showSelectedTrack() {
    Track t = getSelectedTrack();
    if (t == null) {
      infoArea.setText("Click a song to see its info.");
      return;
    }
    String comments = t.comments.isEmpty() ? "(no comments yet)" : t.comments;
    infoArea.setText(
      "Title: " + t.title + "\n" +
      "Artist: " + t.artist + "\n" +
      "Year: " + t.year + "\n" +
      "Genre: " + t.genre + "\n" +
      "Favorite: " + (t.isFavorite ? "Yes " + STAR : "No") + "\n" +
      "File: " + t.filePath + "\n\n" +
      "Your comments:\n" + comments
    );
    infoArea.setCaretPosition(0);
  }

  // ================================================================
  //  HELPERS
  // ================================================================

  // returns the song clicked in the list, or null if none is selected
  private static Track getSelectedTrack() {
    int i = songList.getSelectedIndex();
    if (i == -1) {
      return null;
    }
    return tracks.get(i);
  }

  // updates the song list text, with a star next to favorites
  private static void refreshList() {
    for (int i = 0; i < tracks.size(); i++) {
      Track t = tracks.get(i);
      String star = t.isFavorite ? STAR + " " : "";
      String text = star + t.title + " - " + t.artist;

      if (i < listModel.size()) {
        listModel.set(i, text); // update the row (keeps the selection)
      } else {
        listModel.addElement(text); // first time: add the row
      }
    }
  }

  // shows a message at the bottom of the window
  private static void setStatus(String text) {
    // the window doesn't exist yet if this runs during startup
    if (statusLabel != null) {
      statusLabel.setText("  " + text);
    }
  }

  // ================================================================
  //  COMMENTS AND FAVORITES
  // ================================================================

  private static void saveComment() {
    Track selected = requireSelectedTrack();
    if (selected == null) {
      return;
    }
    selected.comments = commentBox.getText().trim();
    saveChanges();
  }

  private static void toggleFavorite() {
    Track selected = requireSelectedTrack();
    if (selected == null) {
      favoriteBox.setSelected(false); // undo the click
      return;
    }
    selected.isFavorite = favoriteBox.isSelected();
    saveChanges();
  }

  // after a comment or favorite changes: save it and update the screen
  private static void saveChanges() {
    saveDatabase();
    refreshList();
    showSelectedTrack();
  }

  // puts the clicked song's comments and favorite into the comment box
  private static void showSelectedSettings() {
    Track selected = getSelectedTrack();
    if (selected == null) {
      commentBox.setText("");
      favoriteBox.setSelected(false);
    } else {
      commentBox.setText(selected.comments);
      favoriteBox.setSelected(selected.isFavorite);
    }
  }
}