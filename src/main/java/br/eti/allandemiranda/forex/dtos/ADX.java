package br.eti.allandemiranda.forex.dtos;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public record ADX(@NotNull LocalDateTime dateTime, double adx, double diPlus, double diMinus) implements DefaultModel {

}
