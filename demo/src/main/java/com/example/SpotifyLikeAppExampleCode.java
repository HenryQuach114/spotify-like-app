package com.example;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.*;
import java.util.*;
import javax.sound.sampled.*;

// declares a class for the app
public class SpotifyLikeAppExampleCode {

  // the current audio clip
  private static Clip audioClip;
private static Scanner input = new Scanner(System.in);
 

  private static String directoryPath =
    "/Users/henryquach/Documents/GitHub/spotify-like-app/demo/src/main/java/com/example";

  // "main" makes this class a java app that can be executed
  public static void main(final String[] args) {
    // reading audio library from json file
    Song[] library = readAudioLibrary();

    String userInput = "";
    while (!userInput.equals("q")) {
      menu();

      // get input
      userInput = input.nextLine();

      // accept upper or lower case commands
      userInput = userInput.toLowerCase();

      // do something
      handleMenu(userInput, library);
    }

    // close the scanner
    input.close();
  }

  /*
   * displays the menu for the app
   */
  public static void menu() {
    System.out.println();
    System.out.println("---- SpotifyLikeApp ----");
    System.out.println("[H]ome");
    System.out.println("[S]earch by title");
    System.out.println("[L]ibrary");
    System.out.println("[P]lay");
    System.out.println("[Q]uit");

    System.out.println("");
    System.out.print("Enter q to Quit:");
  }

  /*
   * handles the user input for the app
   */
  public static void handleMenu(String userInput, Song[] library) {
    switch (userInput) {
      case "h":
        System.out.println("-->Home<--");
        System.out.println("Welcome to the SpotifyLikeApp!");
        System.out.println("You have " + library.length + " songs in your library.");
        break;
      case "s":
        System.out.println("-->Search by title<--");
        System.out.print("Enter a song title: ");
        String title = input.nextLine();
        boolean found = false;
        for (int i = 0; i < library.length; i++) {
          if (library[i].name().equalsIgnoreCase(title)) {
            play(library, i);
            found = true;
          }
        }
        if (!found) {
          System.out.println("Song not found.");
        }
        break;
      case "l":
        System.out.println("-->Library<--");
        for (int i = 0; i < library.length; i++) {
          System.out.println((i + 1) + ". " + library[i].name() + " - " + library[i].artist());
        }
        System.out.println();
        System.out.print("Enter the number of the song to play: ");
        int songNumber = Integer.parseInt(input.nextLine());
        play(library, songNumber - 1);
        break;
      case "p":
        System.out.println("-->Play<--");
        play(library, 3);
        break;
      case "q":
        System.out.println("-->Quit<--");
        break;
      default:
        break;
    }
  }

  /*
   * plays an audio file
   */
  public static void play(Song[] library, int i) {
    // open the audio file

    // get the filePath and open a audio file
    final String filename = library[i].fileName();
    final String filePath = directoryPath + "/wav/" + filename;
    final File file = new File(filePath);
    System.out.println();
    System.out.println("Now playing: " + library[i].name());
    System.out.println("Artist: " + library[i].artist());
    System.out.println("Year: " + library[i].year());
    System.out.println("Genre: " + library[i].genre());
    System.out.println("File: " + filePath);

    // stop the current song from playing, before playing the next one
    if (audioClip != null) {
      audioClip.close();
    }

    try {
      // create clip
      audioClip = AudioSystem.getClip();

      // get input stream
      final AudioInputStream in = AudioSystem.getAudioInputStream(file);

      audioClip.open(in);
      audioClip.setMicrosecondPosition(0);
      audioClip.loop(Clip.LOOP_CONTINUOUSLY);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // read the audio library of music
  public static Song[] readAudioLibrary() {
    // get the file path
    final String jsonFileName = "audio-library.json";
    final String filePath = directoryPath + "/" + jsonFileName;

    Song[] library = null;
    try {
      System.out.println("Reading the file " + filePath);
      JsonReader reader = new JsonReader(new FileReader(filePath));
      library = new Gson().fromJson(reader, Song[].class);
    } catch (Exception e) {
      System.out.printf("ERROR: unable to read the file %s\n", filePath);
      System.out.println();
    }

    return library;
  }
}
