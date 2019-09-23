package com.googlesource.gerrit.plugins.renameproject;

public class RenameException extends Exception {
  public RenameException(String message, Throwable cause) {
    super(message, cause);
  }
}
