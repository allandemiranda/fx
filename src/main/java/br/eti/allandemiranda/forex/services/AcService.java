package br.eti.allandemiranda.forex.services;

import br.eti.allandemiranda.forex.dtos.AC;
import br.eti.allandemiranda.forex.enums.IndicatorTrend;
import br.eti.allandemiranda.forex.headers.AcHeader;
import br.eti.allandemiranda.forex.repositories.AcRepository;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter(AccessLevel.PRIVATE)
public class AcService {

  private static final String OUTPUT_FILE_NAME = "ac.csv";
  private static final CSVFormat CSV_FORMAT = CSVFormat.TDF.builder().build();

  private final AcRepository repository;

  @Value("${config.root.folder}")
  private File outputFolder;
  @Value("${ac.debug:false}")
  private boolean debugActive;
  @Setter(AccessLevel.PRIVATE)
  private IndicatorTrend lastTrend = IndicatorTrend.NEUTRAL;

  @Autowired
  protected AcService(final AcRepository repository) {
    this.repository = repository;
  }

  private @NotNull String getNumber(final @NotNull BigDecimal value) {
    return new DecimalFormat("#0.00000#").format(value.doubleValue()).replace(".", ",");
  }

  /**
   * Add a new AC value
   *
   * @param candlestickTime The last candlestick data time
   * @param ac              The AV value
   */
  public void addAc(final @NotNull LocalDateTime candlestickTime, final @NotNull BigDecimal ac) {
    this.getRepository().add(candlestickTime, ac);
  }

  /**
   * Get the last AC indicator
   *
   * @return The AC indicator
   */
  public AC[] getAc() {
    return this.getRepository().get();
  }

  @PostConstruct
  private void init() {
    this.printDebugHeader();
  }

  private @NotNull File getOutputFile() {
    return new File(this.getOutputFolder(), OUTPUT_FILE_NAME);
  }

  @SneakyThrows
  private void printDebugHeader() {
    if (this.isDebugActive()) {
      try (final FileWriter fileWriter = new FileWriter(this.getOutputFile()); final CSVPrinter csvPrinter = CSV_FORMAT.print(fileWriter)) {
        csvPrinter.printRecord(Arrays.stream(AcHeader.values()).map(Enum::toString).toArray());
      }
    }
  }

  @SneakyThrows
  public void updateDebugFile(final @NotNull BigDecimal price, final @NotNull IndicatorTrend trend) {
    if (this.isDebugActive()) {
      try (final FileWriter fileWriter = new FileWriter(this.getOutputFile(), true); final CSVPrinter csvPrinter = CSV_FORMAT.print(fileWriter)) {
        final AC ac = this.getRepository().get()[0];
        csvPrinter.printRecord(ac.dateTime().format(DateTimeFormatter.ISO_DATE_TIME),
            trend.equals(IndicatorTrend.BUY) || this.getLastTrend().equals(IndicatorTrend.BUY) ? getNumber(ac.value()) : "",
            trend.equals(IndicatorTrend.SELL) || this.getLastTrend().equals(IndicatorTrend.SELL) ? getNumber(ac.value()) : "",
            trend.equals(IndicatorTrend.NEUTRAL) || this.getLastTrend().equals(IndicatorTrend.NEUTRAL) ? getNumber(ac.value()) : "", getNumber(price));
      }
      this.setLastTrend(trend);
    }
  }
}
