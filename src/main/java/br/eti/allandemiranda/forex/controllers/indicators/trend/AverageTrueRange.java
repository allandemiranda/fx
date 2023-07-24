package br.eti.allandemiranda.forex.controllers.indicators.trend;

import br.eti.allandemiranda.forex.controllers.indicators.Indicator;
import br.eti.allandemiranda.forex.controllers.indicators.SignalTrend;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class AverageTrueRange implements Indicator {

  @Override
  public void run() {
    log.info("Run the AverageTrueRange");
  }

  @Override
  public @NotNull SignalTrend getSignal() {
    log.info("getSignal() the AverageTrueRange");
    return SignalTrend.Out;
  }
}