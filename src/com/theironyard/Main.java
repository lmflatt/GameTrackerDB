package com.theironyard;

import org.h2.tools.Server;

import spark.ModelAndView;
import spark.Session;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {

    static HashMap<String, User> users = new HashMap<>();
    static String warning = "";

    public static void populateUsers(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet results = stmt.executeQuery("SELECT * FROM users");
        while (results.next()) {
            int id = results.getInt("id");
            String userName = results.getString("user_name");
            String password = results.getString("password");
            users.put(userName, new User(id, userName, password));
        }
    }

    public static void insertUser(Connection conn, User u) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (null, ?, ?)");
        stmt.setString(1, u.name);
        stmt.setString(2, u.password);
        stmt.execute();
    }

    public static int getUserId (Connection conn, User user) throws SQLException {
        int id = -1;
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE user_name = ? AND password = ?");
        stmt.setString(1, user.name);
        stmt.setString(2, user.password);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            id = rs.getInt("id");
        }

        return id;
    }

    public static void insertGame(Connection conn, Game g) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO games VALUES (null, ?, ?, ?, ?, ?, true, false)");
        stmt.setInt(1, g.userId);
        stmt.setString(2, g.name);
        stmt.setString(3, g.genre);
        stmt.setString(4, g.platform);
        stmt.setInt(5, g.releaseYear);
        stmt.execute();
    }

    public static ArrayList<Game> selectGames(Connection conn) throws SQLException {
        ArrayList<Game> games = new ArrayList();
        Statement stmt = conn.createStatement();
        ResultSet results = stmt.executeQuery("SELECT * FROM games");
        while (results.next()) {
            int id = results.getInt("id");
            int userId = results.getInt("user_id");
            String name = results.getString("game_name");
            String genre = results.getString("genre");
            String platform = results.getString("platform");
            int releaseYear = results.getInt("release_year");
            boolean display = results.getBoolean("display");

            Game game = new Game(id, userId, name, genre, platform, releaseYear);
            game.display = display;
            games.add(0, game);
        }
        return games;
    }

    public static void deleteGame(Connection conn, int id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("DELETE FROM games WHERE id = ?");
        stmt.setInt(1, id);
        stmt.execute();
    }

    public static void toggleDisplay(Connection conn, int id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("UPDATE games SET display = NOT display WHERE id = ?");
        stmt.setInt(1, id);
        stmt.execute();
    }

    public static void updateGame(Connection conn, Game g) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("UPDATE games SET game_name = ?, genre = ?, platform = ?, release_year = ?, display = true WHERE id = ?");
        stmt.setString(1, g.name);
        stmt.setString(2, g.genre);
        stmt.setString(3, g.platform);
        stmt.setInt(4, g.releaseYear);
        stmt.setInt(5, g.id);
        stmt.execute();
    }


    public static void main(String[] args) throws SQLException {
        Server.createWebServer().start();
        Connection conn = DriverManager.getConnection("jdbc:h2:./main");
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS users (id IDENTITY, user_name VARCHAR, password VARCHAR)");
        stmt.execute("CREATE TABLE IF NOT EXISTS games (id IDENTITY, user_id INT, game_name VARCHAR, genre VARCHAR, platform VARCHAR, release_year INT, display BOOLEAN, mine BOOLEAN)");

        populateUsers(conn);

        Spark.staticFileLocation("/public");
        Spark.init();
        Spark.get(
                "/",
                ((request, response) -> {
                    User user = getUserFromSession(request.session());

                    HashMap m = new HashMap<>();
                    if (user == null) {
                        m.put("warning", warning);
                        return new ModelAndView(m, "login.html");
                    }

                    ArrayList<Game> games = selectGames(conn);

                    for (Game game : games) {
                        if (user.id == game.userId) {
                            game.mine = true;
                        }
                    }

                    m.put("games", games);
                    m.put("user", user);
                    m.put("warning", warning);
                    return new ModelAndView(m, "home.html");
                }),
                new MustacheTemplateEngine()
        );

        Spark.post(
                "/create-user",
                ((request, response) -> {
                    String name = request.queryParams("loginName");
                    String password = request.queryParams("password");

                    if (name.isEmpty() || password.isEmpty()) {
                        warning = "Do not leave any fields blank.";
                        response.redirect("/");
                        return "";
                    }

                    User user = users.get(name);
                    if (user == null) {
                        user = new User(name, password);
                        insertUser(conn, user);
                        user.id = getUserId(conn, user);
                        users.put(name, user);
                    }

                    if (! password.equals(user.password)) {
                        warning = "Incorrect Login Information.";
                        response.redirect("/");
                        return "";
                    }

                    Session session = request.session();
                    session.attribute("userName", name);
                    warning = "";

                    response.redirect("/");
                    return "";
                })
        );

        Spark.post(
                "/create-game",
                ((request, response) -> {
                    User user = getUserFromSession(request.session());
                    if (user == null) {
                        warning = "You are not logged in!";
                        response.redirect("/");
                    }

                    String gameName = request.queryParams("gameName");
                    String gameGenre = request.queryParams("gameGenre");
                    String gamePlatform = request.queryParams("gamePlatform");
                    String year = request.queryParams("gameYear");

                    if (gameName.isEmpty() ||
                        gameGenre == null ||gameGenre.isEmpty() ||
                        gamePlatform == null || gamePlatform.isEmpty() ||
                        year.isEmpty()) {
                        warning = "You cannot leave any fields blank.";
                        response.redirect("/");
                        return "";
                    }

                    int gameYear = Integer.valueOf(year);

                    Game game = new Game(user.id, gameName, gameGenre, gamePlatform, gameYear);

                    insertGame(conn, game);
                    warning = "";

                    response.redirect("/");
                    return "";
                })
        );

        Spark.post(
                "/logout",
                ((request, response) -> {
                    Session session = request.session();
                    session.invalidate();
                    warning = "";
                    response.redirect("/");
                    return "";
                })
        );

        Spark.post(
                "/delete-game",
                ((request, response) -> {
                    Session session = request.session();
                    String userName = session.attribute("userName");
                    User user = users.get(userName);
                    if (user == null) {
                        warning = "How are you not logged in?";
                        response.redirect("/");
                    }

                    int id = Integer.parseInt(request.queryParams("id"));
                    deleteGame(conn, id);

                    response.redirect("/");
                    return "";
                })
        );

        Spark.post(
                "/edit-game",
                ((request, response) -> {
                    Session session = request.session();
                    String userName = session.attribute("userName");
                    User user = users.get(userName);
                    if (user == null) {
                        warning = "How are you not logged in?";
                        response.redirect("/");
                    }

                    int id = Integer.parseInt(request.queryParams("id"));
                    toggleDisplay(conn, id);

                    response.redirect("/");
                    return "";
                })
        );

        Spark.post(
                "/accept-changes",
                ((request, response) -> {
                    Session session = request.session();
                    String userName = session.attribute("userName");
                    User user = users.get(userName);
                    if (user == null) {
                        warning = "How are you not logged in?";
                        response.redirect("/");
                    }

                    int id = Integer.parseInt(request.queryParams("id"));
                    String gameName = request.queryParams("gameName");
                    String gameGenre = request.queryParams("gameGenre");
                    String gamePlatform = request.queryParams("gamePlatform");
                    String year = request.queryParams("gameYear");
                    if (gameName.isEmpty() ||
                        gameGenre == null ||gameGenre.isEmpty() ||
                        gamePlatform == null || gamePlatform.isEmpty() ||
                        year.isEmpty()) {
                        toggleDisplay(conn, id);
                        warning = "You cannot leave any fields blank.";
                        response.redirect("/");
                        return "";
                    }

                    int gameYear = Integer.valueOf(year);

                    Game game = new Game(id, user.id, gameName, gameGenre, gamePlatform, gameYear);

                    updateGame(conn, game);
                    warning = "";

                    response.redirect("/");
                    return "";
                })
        );
    }

    static User getUserFromSession(Session session) {
        String name = session.attribute("userName");
        return users.get(name);
    }
}
