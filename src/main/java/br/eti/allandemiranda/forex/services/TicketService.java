package br.eti.allandemiranda.forex.services;

import br.eti.allandemiranda.forex.dtos.Ticket;
import br.eti.allandemiranda.forex.repositories.TicketRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Getter(AccessLevel.PRIVATE)
@Slf4j
public class TicketService {

  private final TicketRepository repository;

  @Autowired
  protected TicketService(final TicketRepository repository) {
    this.repository = repository;
  }

  /**
   * Get the last ticket generated
   *
   * @return The last ticket
   */
  public @NotNull Ticket getTicket() {
    return this.getRepository().getCurrentTicket();
  }

  /**
   * Check if the ticket information is valid to be used. This happened because the data sometimes come with only BID or ASK value, and for processes of price is
   * necessary to be on database the double values valid.
   *
   * @return If is ready to use the Ticket information
   */
  public boolean isReady() {
    return this.getRepository().getCurrentTicket().bid().compareTo(BigDecimal.ZERO) > 0 && this.getRepository().getCurrentTicket().ask().compareTo(BigDecimal.ZERO) > 0;
  }

  /**
   * Inset new ticket information
   *
   * @param dateTime The new DateTime ticket
   * @param bid      The new BID price ticket (zero if not have a price)
   * @param ask      The new ASK price ticket (zero if not have a price)
   * @return If the update is valid
   */
  @Synchronized
  public boolean updateData(final @NotNull LocalDateTime dateTime, final double bid, final double ask) {
    if (dateTime.isAfter(this.getRepository().getCurrentTicket().dateTime())) {
      this.getRepository().update(dateTime, bid, ask);
      return true;
    } else {
      // log.warn("Bad input ticket dataTime={} bid={} ask={}", dateTime.format(DateTimeFormatter.ISO_DATE_TIME), bid, ask);
      return false;
    }
  }
}
