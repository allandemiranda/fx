package br.eti.allandemiranda.forex.services;

import br.eti.allandemiranda.forex.dtos.Ticket;
import br.eti.allandemiranda.forex.headers.TicketHeader;
import br.eti.allandemiranda.forex.repositories.TicketRepository;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter(AccessLevel.PRIVATE)
public class TicketService {

  private static final String OUTPUT_FILE_NAME = "tickets.csv";
  private static final CSVFormat CSV_FORMAT = CSVFormat.TDF.builder().build();

  private final TicketRepository repository;

  @Value("${config.root.folder}")
  private File outputFolder;
  @Value("${ticket.debug:false}")
  private boolean debugActive;
  @Value("${ticket.digits:5}")
  private int digits;

  @Autowired
  protected TicketService(final TicketRepository repository) {
    this.repository = repository;
  }

  private static @NotNull String getNumber(final double value) {
    return new DecimalFormat("#0.00000#").format(value).replace(".", ",");
  }

  private @NotNull File getOutputFile() {
    return new File(this.getOutputFolder(), OUTPUT_FILE_NAME);
  }

  @PostConstruct
  private void init() {
    this.printDebugHeader();
  }

  @SneakyThrows
  private void printDebugHeader() {
    if (this.isDebugActive()) {
      try (final FileWriter fileWriter = new FileWriter(this.getOutputFile()); final CSVPrinter csvPrinter = CSV_FORMAT.print(fileWriter)) {
        csvPrinter.printRecord(Arrays.stream(TicketHeader.values()).map(Enum::toString).toArray());
      }
    }
  }

  @SneakyThrows
  private void updateDebugFile(final @NotNull TicketRepository repository) {
    final Ticket currentTicket = repository.getCurrentTicket();
    if (this.isDebugActive() && this.isReady()) {
      try (final FileWriter fileWriter = new FileWriter(this.getOutputFile(), true); final CSVPrinter csvPrinter = CSV_FORMAT.print(fileWriter)) {
        csvPrinter.printRecord(currentTicket.dateTime().format(DateTimeFormatter.ISO_DATE_TIME), getNumber(currentTicket.bid()), getNumber(currentTicket.ask()),
            this.getCurrentSpread());
      }
    }
  }

  public int getCurrentSpread() {
    final Ticket currentTicket = this.getRepository().getCurrentTicket();
    return (int) ((currentTicket.ask() - currentTicket.bid()) / (1 / (Math.pow(10, this.getDigits()))));
  }

  public @NotNull Ticket getTicket() {
    return this.getRepository().getCurrentTicket();
  }

  public boolean isReady() {
    return this.getRepository().getCurrentTicket().bid() > 0d && this.getRepository().getCurrentTicket().ask() > 0d;
  }

  @Synchronized
  public void updateData(final @NotNull Ticket ticket) {
    this.getRepository().update(ticket);
    this.updateDebugFile(this.getRepository());
  }
}
