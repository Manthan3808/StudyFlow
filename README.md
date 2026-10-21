<p align="center">
  <img src="StudyFlow.png" alt="StudyFlow icon" width="120">
</p>

<h1 align="center">StudyFlow</h1>

<p align="center">
  A gamified study planner for students, built in Java.<br>
  Track tasks, take quizzes, earn badges, and keep your virtual study buddy happy.
</p>

---

## Features

- **Login and register**: create your own account. Each person's data stays on their own computer.
- **Task tracker**: add, complete and manage your study tasks.
- **Quiz**: test yourself and earn progress.
- **Badges**: unlock achievements as you complete tasks and quizzes.
- **Virtual character**: your study buddy is happy when you complete tasks and sad when you ignore them.
- **Focus mode**: stay on one thing at a time.

## Download and run

1. Install **Java 8 or newer**. A free option is [Adoptium Temurin](https://adoptium.net).
2. Go to the [Releases page](https://github.com/Manthan3808/StudyFlow/releases) and download `StudyFlow.jar`.
3. Double-click `StudyFlow.jar`.

If double-click doesn't work, open a terminal in the folder with the file and run:

```
java -jar StudyFlow.jar
```

## Run from source

```
javac --release 8 StudyFlow.java
java StudyFlow
```

To build the JAR yourself:

```
javac --release 8 StudyFlow.java
echo Main-Class: StudyFlow > manifest.txt
jar cfm StudyFlow.jar manifest.txt *.class
```

## Your data

Accounts and progress are saved on your own computer, in your user folder (for example `C:\Users\YourName` on Windows). Passwords are stored scrambled, not as plain text. Nothing is sent over the internet.

## Tech

- Java (Swing desktop app)
- Compiled with `--release 8`, so it runs on Java 8 and newer

## Author

Made by **Manthan Prajapati**.
