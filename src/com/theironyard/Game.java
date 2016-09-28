package com.theironyard;

public class Game {
    int id;
    int userId;
    String name;
    String genre;
    String platform;
    int releaseYear;
    boolean display = true;
    boolean mine = false;

    public Game(){}

    public Game(int userId, String name, String genre, String platform, int releaseYear) {
        this.userId = userId;
        this.name = name;
        this.genre = genre;
        this.platform = platform;
        this.releaseYear = releaseYear;
    }

    public Game(int id, int userId, String name, String genre, String platform, int releaseYear) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.genre = genre;
        this.platform = platform;
        this.releaseYear = releaseYear;
    }
}
