package com.quizsphere;

import java.io.Console;
import java.util.Map;
import java.util.Scanner;

/**
 * Creates the administrator account. The password is typed at the prompt (hidden when a console is
 * available), is hashed with PBKDF2 and is never written to a file or hard-coded.
 * Run:  java -cp "out;lib\*" com.quizsphere.CreateAdmin
 */
public final class CreateAdmin {
    private CreateAdmin() { }

    public static void main(String[] args) {
        try {
            Config config = Config.load();
            AuthenticationService auth = new AuthenticationService(new DatabaseManager(config), config);
            Console console = System.console();
            Scanner in = console == null ? new Scanner(System.in) : null;
            if (console == null) System.out.println("(No secure console: the password will be visible while typing.)");
            String username = ask(console, in, "Admin username: ");
            String email = ask(console, in, "Admin email: ");
            String name = ask(console, in, "Full name: ");
            String pw1 = askSecret(console, in, "Password (8+ characters, letters and digits): ");
            String pw2 = askSecret(console, in, "Confirm password: ");
            User admin = auth.createAdmin(username, email, name, pw1, pw2);
            System.out.println("Administrator '" + admin.getUsername() + "' created. Log in at the website with this username.");
        } catch (Errors.AppError e) {
            System.err.println("Could not create the administrator: " + e.getMessage());
            for (Map.Entry<String, String> f : e.fields().entrySet()) System.err.println("  - " + f.getKey() + ": " + f.getValue());
            System.exit(1);
        } catch (Config.ConfigException | DatabaseException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String ask(Console c, Scanner in, String prompt) {
        if (c != null) return c.readLine("%s", prompt);
        System.out.print(prompt);
        return in.nextLine();
    }

    private static String askSecret(Console c, Scanner in, String prompt) {
        if (c != null) return new String(c.readPassword("%s", prompt));
        System.out.print(prompt);
        return in.nextLine();
    }
}
