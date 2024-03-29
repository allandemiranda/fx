package br.eti.allandemiranda.forex.services;

import br.eti.allandemiranda.forex.dtos.Order;
import br.eti.allandemiranda.forex.dtos.Signal;
import br.eti.allandemiranda.forex.dtos.Ticket;
import br.eti.allandemiranda.forex.enums.OrderPosition;
import br.eti.allandemiranda.forex.enums.OrderStatus;
import br.eti.allandemiranda.forex.enums.SignalTrend;
import br.eti.allandemiranda.forex.headers.OrderHeader;
import br.eti.allandemiranda.forex.repositories.OrderRepository;
import br.eti.allandemiranda.forex.repositories.StatisticRepository;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
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
public class OrderService {

  public static final String TIME_OPEN = "0 00:00:00";
  private static final String TIME_OPEN_FORMAT = "%sd %s:%s:%s";
  private static final String OUTPUT_FILE_NAME = "order.csv";
  private static final CSVFormat CSV_FORMAT = CSVFormat.TDF.builder().build();

  private final OrderRepository repository;
  private final StatisticRepository statisticRepository;
  @Value("${order.open.onlyStrong:false}")
  private boolean isOpenOnlyStrong;
  @Value("${order.open.maxOpenPositions:999}")
  private int maxOpenPositions;
  @Value("${order.open.monday.start:00:00:00}")
  private String mondayStart;
  @Value("${order.open.monday.end:23:59:59}")
  private String mondayEnd;
  @Value("${order.open.tuesday.start:00:00:00}")
  private String tuesdayStart;
  @Value("${order.open.tuesday.end:23:59:59}")
  private String tuesdayEnd;
  @Value("${order.open.wednesday.start:00:00:00}")
  private String wednesdayStart;
  @Value("${order.open.wednesday.end:23:59:59}")
  private String wednesdayEnd;
  @Value("${order.open.thursday.start:00:00:00}")
  private String thursdayStart;
  @Value("${order.open.thursday.end:23:59:59}")
  private String thursdayEnd;
  @Value("${order.open.friday.start:00:00:00}")
  private String fridayStart;
  @Value("${order.open.friday.end:23:59:59}")
  private String fridayEnd;
  @Value("${order.open.spread.max:12}")
  private int maxSpread;
  @Value("${order.safe.take-profit:150}")
  private int takeProfit;
  @Value("${order.safe.stop-loss:100}")
  private int stopLoss;
  @Value("${order.swap.long:-5.46}")
  private double swapLong;
  @Value("${order.swap.short:0.61}")
  private double swapShort;
  @Value("${order.swap.rate.triple:WEDNESDAY}")
  private String swapRateTriple;
  // The minimal diff between the lines that indicate trading to open a position
  @Value("${order.open.trading.min:-1}")
  private int minTradingDiff;
  @Value("${order.debug:true}")
  private boolean debugActive;
  @Value("${config.root.folder}")
  private File outputFolder;
  @Setter(AccessLevel.PRIVATE)
  private LocalDateTime lastSignalOpenDateTime = LocalDateTime.MIN;
  @Setter(AccessLevel.PRIVATE)
  private BigDecimal currentBalance = BigDecimal.ZERO;
  @Setter(AccessLevel.PRIVATE)
  private BigDecimal lastOpenBalance = BigDecimal.ZERO;

  @Autowired
  protected OrderService(final OrderRepository repository, final StatisticRepository statisticRepository) {
    this.repository = repository;
    this.statisticRepository = statisticRepository;
  }

  /**
   * String number format to price value
   *
   * @param value The price value
   * @return The text price value
   */
  private @NotNull String getNumberPrice(final @NotNull BigDecimal value) {
    return new DecimalFormat("#0.00000#").format(value.doubleValue()).replace(".", ",");
  }

  /**
   * String number format to balance value
   *
   * @param value The balance value
   * @return The text balance value
   */
  private @NotNull String getNumberBalance(final @NotNull BigDecimal value) {
    return new DecimalFormat("#0.00#").format(value.doubleValue()).replace(".", ",");
  }

  private int getPoints(final @NotNull BigDecimal price, final int digits) {
    return price.multiply(BigDecimal.valueOf(Math.pow(10, digits))).intValue();
  }

  /**
   * Add a ticket and signal to the database make the calculation and organization
   *
   * @param ticket The current ticket
   * @param signal the current signal information
   */
  public void insertTicketAndSignal(final @NotNull Ticket ticket, final @NotNull Signal signal, final int tpDiff) {
    // Update open tickets
    this.updateTicket(Arrays.stream(this.getRepository().getOrders()).toList(), ticket, this.getTakeProfit(), this.getStopLoss(), BigDecimal.valueOf(this.getSwapLong()),
        BigDecimal.valueOf(this.getSwapShort()), DayOfWeek.valueOf(this.getSwapRateTriple())).forEach(order -> this.getRepository().updateOrder(order));

    // Check to open a new order
    if (checkDataTime(ticket.dateTime()) && tpDiff >= this.getMinTradingDiff()) {
      final Optional<Order> openOrder = this.openOrder(ticket, signal, this.getMaxOpenPositions(), tpDiff);
      if (openOrder.isPresent()) {
        this.getRepository().addOrder(openOrder.get());
        this.setLastSignalOpenDateTime(signal.dataTime());
      }
    }

    // Update the current balance
    this.setCurrentBalance(this.getNewBalance(this.getRepository().getOrders(), this.getCurrentBalance(), this.getLastOpenBalance()));

    // Print the close orders
    final Order[] orders = this.getRepository().getOrders();
    if(this.isDebugActive()) {
      Arrays.stream(orders).filter(order -> !order.orderStatus().equals(OrderStatus.OPEN)).forEachOrdered(order -> this.updateDebugFile(order, this.getCurrentBalance()));
    }
    Arrays.stream(orders).filter(order -> order.orderStatus().equals(OrderStatus.CLOSE_TP)).forEachOrdered(order -> this.getStatisticRepository().addResultWin(order.openDateTime()));
    Arrays.stream(orders).filter(order -> order.orderStatus().equals(OrderStatus.CLOSE_SL)).forEachOrdered(order -> this.getStatisticRepository().addResultLose(order.openDateTime()));
    this.getStatisticRepository().setBalance(this.getCurrentBalance());

    // Remove che closed orders
    this.getRepository().removeCloseOrders();
  }

  /**
   * Get the curretn balance of close orders
   *
   * @param orders The full orders on this roand
   * @return The new current balance
   */
  private @NotNull BigDecimal getNewBalance(final Order @NotNull [] orders, final @NotNull BigDecimal prevBalance, final @NotNull BigDecimal lastOpenBalance) {
    final BigDecimal currentOpenProfit = Arrays.stream(orders).filter(order -> order.orderStatus().equals(OrderStatus.OPEN))
        .map(order -> order.swapProfit().add(BigDecimal.valueOf(order.currentProfit()))).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal balance = currentOpenProfit.subtract(lastOpenBalance).add(prevBalance);
    this.setLastOpenBalance(currentOpenProfit);
    final BigDecimal currentCloseProfit = Arrays.stream(orders).filter(order -> !order.orderStatus().equals(OrderStatus.OPEN))
        .map(order -> order.swapProfit().add(BigDecimal.valueOf(order.currentProfit()))).reduce(BigDecimal.ZERO, BigDecimal::add);
    return balance.add(currentCloseProfit);
  }

  /**
   * Update data information for positions open
   *
   * @param ticket     The current ticket
   * @param takeProfit The take profit
   * @param stopLoss   The stop loss profit
   * @param swapLong   The swap long in points
   * @param swapShort  The swap short in points
   * @return The list of orders to be updated
   */
  private @NotNull Collection<Order> updateTicket(final @NotNull Collection<Order> orders, final @NotNull Ticket ticket, final int takeProfit, final int stopLoss,
      final @NotNull BigDecimal swapLong, final @NotNull BigDecimal swapShort, final DayOfWeek swapRateTriple) {
    return orders.parallelStream().map(order -> {
      // Check if is necessary add a swap to this order
      final BigDecimal swapProfit = getSwapProfitProcess(ticket, swapLong, swapShort, swapRateTriple, order);

      // Update open time
      final LocalDateTime openDateTime = order.openDateTime();
      final LocalDateTime ticketDateTime = ticket.dateTime();
      final String timeOpen = String.format(TIME_OPEN_FORMAT, ChronoUnit.DAYS.between(openDateTime, ticketDateTime),
          ChronoUnit.HOURS.between(openDateTime, ticketDateTime) % 24, ChronoUnit.MINUTES.between(openDateTime, ticketDateTime) % 60,
          ChronoUnit.SECONDS.between(openDateTime, ticketDateTime) % 60);

      // Update the profit to this order
      final BigDecimal closePrice = getClosePrice(ticket, order);
      final int currentProfit = getCurrentProfit(ticket, order, closePrice);
      final int highProfit = getHighProfit(order, currentProfit);
      final int lowProfit = getLowProfit(order, currentProfit);

      // Update status of this order
      final OrderStatus orderStatus = getOrderStatus(takeProfit, stopLoss, currentProfit);

      // Update order
      return new Order(openDateTime, order.signalDateTime(), order.signalTrend(), ticketDateTime, timeOpen, orderStatus, order.orderPosition(), order.tradingPerformanceDiff(), order.openPrice(),
          closePrice, highProfit, lowProfit, currentProfit, swapProfit);
    }).collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * Get tge order status
   *
   * @param takeProfit    The take profit
   * @param stopLoss      The stop loss (positive value)
   * @param currentProfit the current profit
   * @return The new status to the order
   */
  private @NotNull OrderStatus getOrderStatus(final int takeProfit, final int stopLoss, final int currentProfit) {
    if (currentProfit >= takeProfit) {
      return OrderStatus.CLOSE_TP;
    } else if (currentProfit <= Math.negateExact(stopLoss)) {
      return OrderStatus.CLOSE_SL;
    } else {
      return OrderStatus.OPEN;
    }
  }

  /**
   * Get new low profit
   *
   * @param order         The new order
   * @param currentProfit The current profit
   * @return The low value to this order
   */
  private int getLowProfit(final @NotNull Order order, final int currentProfit) {
    return currentProfit < order.lowProfit() ? order.currentProfit() : order.lowProfit();
  }

  /**
   * Get new high profit
   *
   * @param order         The new order
   * @param currentProfit The current profit
   * @return The high value to this order
   */
  private int getHighProfit(final @NotNull Order order, final int currentProfit) {
    return currentProfit > order.highProfit() ? order.currentProfit() : order.highProfit();
  }

  /**
   * Get current profit
   *
   * @param ticket     The current ticket
   * @param order      The current order
   * @param closePrice The close price
   * @return The new current profit
   */
  private int getCurrentProfit(final @NotNull Ticket ticket, final @NotNull Order order, final @NotNull BigDecimal closePrice) {
    int currentProfit;
    if (order.orderPosition().equals(OrderPosition.BUY)) {
      currentProfit = this.getPoints(closePrice, ticket.digits()) - this.getPoints(order.openPrice(), ticket.digits());
    } else {
      currentProfit = this.getPoints(order.openPrice(), ticket.digits()) - this.getPoints(closePrice, ticket.digits());
    }
    return currentProfit;
  }

  /**
   * Get close price
   *
   * @param ticket The current ticket
   * @param order  The current order
   * @return The new close price
   */
  private @NotNull BigDecimal getClosePrice(final @NotNull Ticket ticket, final @NotNull Order order) {
    if (order.orderPosition().equals(OrderPosition.BUY)) {
      return ticket.bid();
    } else {
      return ticket.ask();
    }
  }

  /**
   * Check if is necessary add a swap to this order
   *
   * @param ticket         The current ticket
   * @param swapLong       The swap-long points
   * @param swapShort      The swap short points
   * @param swapRateTriple The day of the week to swap tripe
   * @param order          The current order to be updated
   * @return The new swap balance value
   */
  private @NotNull BigDecimal getSwapProfitProcess(final @NotNull Ticket ticket, final @NotNull BigDecimal swapLong, final @NotNull BigDecimal swapShort,
      final @NotNull DayOfWeek swapRateTriple, final @NotNull Order order) {
    if (!order.lastUpdateDateTime().getDayOfWeek().equals(ticket.dateTime().getDayOfWeek())) {
      BigDecimal points;
      if (OrderPosition.BUY.equals(order.orderPosition())) {
        points = swapRateTriple.equals(order.lastUpdateDateTime().getDayOfWeek()) ? swapLong.multiply(BigDecimal.valueOf(3)) : swapLong;
      } else {
        points = swapRateTriple.equals(order.lastUpdateDateTime().getDayOfWeek()) ? swapShort.multiply(BigDecimal.valueOf(3)) : swapShort;
      }
      return order.swapProfit().add(points);
    } else {
      return order.swapProfit();
    }
  }

  /**
   * Open a new order if possible
   *
   * @param ticket           The current ticket
   * @param signal           The current signal
   * @param maxOpenPositions The maximum number of open orders
   * @return The possible new order to open
   */
  private @NotNull Optional<Order> openOrder(final @NotNull Ticket ticket, final @NotNull Signal signal, final int maxOpenPositions, final int tpDiff) {
    final LocalDateTime signalDateTime = signal.dataTime();
    final SignalTrend trend = signal.trend();
    if (this.getRepository().numberOfOrdersOpen() < maxOpenPositions && ticket.spread() <= this.getMaxSpread() && signalDateTime.isAfter(
        this.getLastSignalOpenDateTime())) {
      if (this.isOpenOnlyStrong() && trend.equals(SignalTrend.STRONG_BUY)) {
        return Optional.of(generateOpenOrder(ticket, signalDateTime, trend, OrderPosition.BUY, tpDiff));
      } else if (this.isOpenOnlyStrong() && trend.equals(SignalTrend.STRONG_SELL)) {
        return Optional.of(generateOpenOrder(ticket, signalDateTime, trend, OrderPosition.SELL, tpDiff));
      } else if (!this.isOpenOnlyStrong() && (trend.equals(SignalTrend.STRONG_BUY) || trend.equals(SignalTrend.BUY))) {
        return Optional.of(generateOpenOrder(ticket, signalDateTime, trend, OrderPosition.BUY, tpDiff));
      } else if (!this.isOpenOnlyStrong() && (trend.equals(SignalTrend.STRONG_SELL) || trend.equals(SignalTrend.SELL))) {
        return Optional.of(generateOpenOrder(ticket, signalDateTime, trend, OrderPosition.SELL, tpDiff));
      }
    }
    return Optional.empty();
  }

  /**
   * Generate an open order to be sent
   *
   * @param ticket         The current Ticket
   * @param signalDateTime The Signal data time
   * @param trend          The trend to the new order
   * @param orderPosition  The position to the new order
   * @return The new order
   */
  private @NotNull Order generateOpenOrder(final @NotNull Ticket ticket, final @NotNull LocalDateTime signalDateTime, final @NotNull SignalTrend trend,
      final @NotNull OrderPosition orderPosition, final int tpDiff) {
    final LocalDateTime ticketDateTime = ticket.dateTime();
    final BigDecimal openPrice = orderPosition.equals(OrderPosition.BUY) ? ticket.ask() : ticket.bid();
    final BigDecimal closePrice = orderPosition.equals(OrderPosition.BUY) ? ticket.bid() : ticket.ask();
    final int spread = Math.negateExact(ticket.spread());
    return new Order(ticketDateTime, signalDateTime, trend, ticketDateTime, TIME_OPEN, OrderStatus.OPEN, orderPosition, tpDiff, openPrice, closePrice, spread, spread, spread,
        BigDecimal.ZERO);
  }

  /**
   * Get confirmation time
   *
   * @param startTime The start time to open an order
   * @param endTime   The end time to close an order
   * @param localTime The current time
   * @return If you can open on this day an order
   */
  private boolean getDataConfirmation(final @NotNull String startTime, final @NotNull String endTime, final @NotNull LocalTime localTime) {
    final LocalTime start = LocalTime.parse(startTime, DateTimeFormatter.ISO_TIME);
    final LocalTime end = LocalTime.parse(endTime, DateTimeFormatter.ISO_TIME);
    return !localTime.isBefore(start) && !localTime.isAfter(end);
  }

  /**
   * Check if the day of the week and time is able to open an order
   *
   * @param dateTime The current data time
   * @return If you can open
   */
  private boolean checkDataTime(final @NotNull LocalDateTime dateTime) {
    final LocalTime localTime = dateTime.toLocalTime();
    return switch (dateTime.getDayOfWeek()) {
      case MONDAY -> getDataConfirmation(this.getMondayStart(), this.getMondayEnd(), localTime);
      case TUESDAY -> getDataConfirmation(this.getTuesdayStart(), this.getTuesdayEnd(), localTime);
      case WEDNESDAY -> getDataConfirmation(this.getWednesdayStart(), this.getWednesdayEnd(), localTime);
      case THURSDAY -> getDataConfirmation(this.getThursdayStart(), this.getThursdayEnd(), localTime);
      case FRIDAY -> getDataConfirmation(this.getFridayStart(), this.getFridayEnd(), localTime);
      case SUNDAY, SATURDAY -> false;
    };
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
        csvPrinter.printRecord(Arrays.stream(OrderHeader.values()).map(Enum::toString).toArray());
      }
    }
  }

  @SneakyThrows
  private void updateDebugFile(final @NotNull Order order, final @NotNull BigDecimal currentBalance) {
    try (final FileWriter fileWriter = new FileWriter(this.getOutputFile(), true); final CSVPrinter csvPrinter = CSV_FORMAT.print(fileWriter)) {
      csvPrinter.printRecord(order.openDateTime().format(DateTimeFormatter.ISO_DATE_TIME), order.signalDateTime().format(DateTimeFormatter.ISO_DATE_TIME),
          order.signalTrend(), order.lastUpdateDateTime().format(DateTimeFormatter.ISO_DATE_TIME), order.timeOpen(), order.orderStatus(), order.orderPosition(),
          order.tradingPerformanceDiff(), getNumberPrice(order.openPrice()), getNumberPrice(order.closePrice()), order.highProfit(), order.lowProfit(), order.currentProfit(),
          getNumberBalance(order.swapProfit()), getNumberBalance(currentBalance));
    }
  }
}
