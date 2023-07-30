package br.eti.allandemiranda.forex.controllers.chart;

import br.eti.allandemiranda.forex.dtos.Ticket;
import br.eti.allandemiranda.forex.services.CandlestickService;
import br.eti.allandemiranda.forex.services.TicketService;
import br.eti.allandemiranda.forex.utils.TimeFrame;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Synchronized;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

@Controller
@Getter(AccessLevel.PRIVATE)
public class ChartProcessor {

  private final CandlestickService candlestickService;
  private final TicketService ticketService;

  @Value("${chart.timeframe:M15}")
  private String timeFrame;

  @Autowired
  protected ChartProcessor(final CandlestickService candlestickService, final TicketService ticketService) {
    this.candlestickService = candlestickService;
    this.ticketService = ticketService;
  }

  private static @NotNull LocalDateTime getCandleDateTime(final @NotNull LocalDateTime dataTime, final @NotNull TimeFrame timeFrame) {
    return switch (timeFrame) {
      case M1 -> getDateTimeLowM(dataTime, 1);
      case M5 -> getDateTimeLowM(dataTime, 5);
      case M15 -> getDateTimeToM15(dataTime);
      case M30 -> getDateTimeToM30(dataTime);
      case H1 -> getDateTimeLowH(dataTime, 1);
      case H2 -> getDateTimeLowH(dataTime, 2);
    };
  }

  private static @NotNull LocalDateTime getDateTimeLowM(final @NotNull LocalDateTime ticketDateTime, final int timeFrameMin) {
    final int oneHourMin = 60;
    final int[] minArray = IntStream.rangeClosed(0, oneHourMin / timeFrameMin).map(operand -> oneHourMin * timeFrameMin).toArray();
    final int index = IntStream.range(1, minArray.length).filter(i -> ticketDateTime.getMinute() < minArray[i]).findFirst().orElseThrow(IllegalStateException::new);
    return LocalDateTime.of(ticketDateTime.toLocalDate(), LocalTime.of(ticketDateTime.getHour(), minArray[index - 1]));
  }

  private static @NotNull LocalDateTime getDateTimeLowH(final @NotNull LocalDateTime ticketDateTime, final int timeFrameHour) {
    final int oneDay = 24;
    final int[] hourArray = IntStream.rangeClosed(0, oneDay / timeFrameHour).map(operand -> oneDay * timeFrameHour).toArray();
    final int index = IntStream.range(1, hourArray.length).filter(i -> ticketDateTime.getMinute() < hourArray[i]).findFirst().orElseThrow(IllegalStateException::new);
    return LocalDateTime.of(ticketDateTime.toLocalDate(), LocalTime.of(hourArray[index - 1], 0));
  }

  private static @NotNull LocalDateTime getDateTimeToM15(final @NotNull LocalDateTime ticketDateTime) {
    final LocalDate localDate = ticketDateTime.toLocalDate();
    final int hour = ticketDateTime.getHour();
    final int minute = ticketDateTime.getMinute();
    if (minute < 15) {
      return LocalDateTime.of(localDate, LocalTime.of(hour, 0));
    } else if (minute < 30) {
      return LocalDateTime.of(localDate, LocalTime.of(hour, 15));
    } else if (minute < 45) {
      return LocalDateTime.of(localDate, LocalTime.of(hour, 30));
    } else {
      return LocalDateTime.of(localDate, LocalTime.of(hour, 45));
    }
  }

  private static @NotNull LocalDateTime getDateTimeToM30(final @NotNull LocalDateTime ticketDateTime) {
    final LocalDate localDate = ticketDateTime.toLocalDate();
    final int hour = ticketDateTime.getHour();
    if (ticketDateTime.getMinute() < 30) {
      return LocalDateTime.of(localDate, LocalTime.of(hour, 0));
    } else {
      return LocalDateTime.of(localDate, LocalTime.of(hour, 30));
    }
  }

  @Synchronized
  public void run() {
    final Ticket ticket = this.ticketService.getCurrentTicket();
    final Ticket ticketReformed = new Ticket(getCandleDateTime(ticket.dateTime(), this.getTimeFrame()), ticket.bid(), ticket.ask());
    this.candlestickService.addTicket(ticketReformed);
    this.candlestickService.updateDebugFile(ticket.dateTime());
  }

  private @NotNull TimeFrame getTimeFrame() {
    return TimeFrame.valueOf(this.timeFrame);
  }
}