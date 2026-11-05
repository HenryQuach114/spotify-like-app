package com.example;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

  // star symbol shown next to favorite songs
  private static final String STAR = "★";

  // all the songs in the app
  private static List<Track> tracks = new ArrayList<>();

  // parts of the window we need to update later
  private static JFrame frame;
  private static JPanel screens; // holds the Home and Library screens
  private static JTextArea welcomeArea;
  private static DefaultListModel<String> listModel;
  private static JList<String> songList;
  private static JLabel nowPlayingLabel;
  private static JTextArea infoArea;
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

  // reads the songs from audio-library.json and turns each one into a Track
  private static void loadTracks() {
    Song[] library = readJson(new File(directoryPath, "audio-library.json"), Song[].class);
    if (library == null) {
      return; // nothing to load
    }

    for (Song song : library) {
      Track t = new Track();
      t.title = song.name();
      t.artist = song.artist();
      t.year = song.year();
      t.genre = song.genre();
      t.filePath = REPO_FOLDER + "/wav/" + song.fileName();
      t.comments = "";
      tracks.add(t);
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
    right.add(infoScroll, BorderLayout.CENTER);

    JPanel libraryScreen = new JPanel(new GridLayout(1, 2, 10, 0));
    libraryScreen.add(listScroll);
    libraryScreen.add(right);

    // when you click a song, show its info
    songList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        showSelectedTrack();
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
    setStatus("Found: " + tracks.get(match).title);
  }

  private static void library() {
    showScreen("library");
    setStatus("Library");
  }

  private static void quit() {
    System.exit(0);
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
}