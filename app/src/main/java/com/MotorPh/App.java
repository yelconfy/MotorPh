package com.MotorPh;

import Forms.LoginForm;

/**
 * Gradle's entry point (build.gradle: mainClass = "com.MotorPh.App").
 *
 * Delegates to Forms.LoginForm.main so there is exactly ONE startup path. This
 * class used to duplicate it while silently skipping FlatLightLaf.setup()
 * (MPH-02), SwingUtilities.invokeLater, and — once MPH-46 landed —
 * DatabaseConnector.Warmup(). Anything added to LoginForm.main was simply never
 * executed, which is why the pool was still being built lazily inside the login
 * itself. Keep this a one-line delegate; do not re-add startup logic here.
 */
public class App {

  public static void main(String[] args) {
    LoginForm.main(args);
  }
}