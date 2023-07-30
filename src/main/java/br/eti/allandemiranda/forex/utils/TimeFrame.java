package br.eti.allandemiranda.forex.utils;

public enum TimeFrame {
  M1("M1"), M5("M5"), M15("M15"), M30("M30"), H1("H1"), H2("H2");

  public final String label;

  TimeFrame(String label) {
    this.label = label;
  }
}