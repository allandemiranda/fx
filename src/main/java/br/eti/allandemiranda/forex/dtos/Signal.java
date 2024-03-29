package br.eti.allandemiranda.forex.dtos;

import br.eti.allandemiranda.forex.enums.SignalTrend;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public record Signal(@NotNull LocalDateTime dataTime, @NotNull SignalTrend trend) {

}
