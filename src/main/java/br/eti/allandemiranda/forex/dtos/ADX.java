package br.eti.allandemiranda.forex.dtos;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public record ADX(@NotNull LocalDateTime dateTime, @NotNull BigDecimal value, @NotNull BigDecimal diPlus, @NotNull BigDecimal diMinus) {

}
