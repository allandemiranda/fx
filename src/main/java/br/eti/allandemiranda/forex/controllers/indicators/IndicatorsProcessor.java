package br.eti.allandemiranda.forex.controllers.indicators;

import br.eti.allandemiranda.forex.controllers.indicators.trend.AverageDirectionalMovementIndex;
import br.eti.allandemiranda.forex.controllers.indicators.trend.CommodityChannelIndex;
import br.eti.allandemiranda.forex.controllers.indicators.trend.EnvelopesTechnicalIndicator;
import br.eti.allandemiranda.forex.controllers.indicators.trend.RelativeStrengthIndex;
import br.eti.allandemiranda.forex.controllers.indicators.trend.RelativeVigorIndex;
import br.eti.allandemiranda.forex.exceptions.IndicatorsException;
import br.eti.allandemiranda.forex.services.CandlestickService;
import br.eti.allandemiranda.forex.services.IndicatorService;
import br.eti.allandemiranda.forex.services.SignalService;
import br.eti.allandemiranda.forex.utils.IndicatorTrend;
import br.eti.allandemiranda.forex.utils.SignalTrend;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

@Controller
@Getter(AccessLevel.PRIVATE)
public class IndicatorsProcessor {

  private static final String ADX = "ADX";
  private static final String ENVELOPES = "Envelopes";
  private static final String CCI = "CCI";
  private static final String RSI = "RSI";
  private static final String RVI = "RVI";

  private final AverageDirectionalMovementIndex averageDirectionalMovementIndex;
  private final EnvelopesTechnicalIndicator envelopesTechnicalIndicator;
  private final CommodityChannelIndex commodityChannelIndex;
  private final RelativeStrengthIndex relativeStrengthIndex;
  private final RelativeVigorIndex relativeVigorIndex;
  private final IndicatorService indicatorService;
  private final SignalService signalService;
  private final CandlestickService candlestickService;

  @Value("${indicators.run.min:3}")
  private int interval;
  @Setter(AccessLevel.PRIVATE)
  private LocalDateTime lastDataTime = LocalDateTime.MIN;

  @Autowired
  protected IndicatorsProcessor(final AverageDirectionalMovementIndex averageDirectionalMovementIndex, final EnvelopesTechnicalIndicator envelopesTechnicalIndicator,
      final CommodityChannelIndex commodityChannelIndex, final RelativeStrengthIndex relativeStrengthIndex, final IndicatorService indicatorService,
      final SignalService signalService, final CandlestickService candlestickService, final RelativeVigorIndex relativeVigorIndex) {
    this.averageDirectionalMovementIndex = averageDirectionalMovementIndex;
    this.envelopesTechnicalIndicator = envelopesTechnicalIndicator;
    this.commodityChannelIndex = commodityChannelIndex;
    this.relativeStrengthIndex = relativeStrengthIndex;
    this.indicatorService = indicatorService;
    this.signalService = signalService;
    this.candlestickService = candlestickService;
    this.relativeVigorIndex = relativeVigorIndex;
  }

  @PostConstruct
  public void init() {
    this.getIndicatorService().addIndicator(ADX, this.getAverageDirectionalMovementIndex());
    this.getIndicatorService().addIndicator(ENVELOPES, this.getEnvelopesTechnicalIndicator());
    this.getIndicatorService().addIndicator(CCI, this.getCommodityChannelIndex());
    this.getIndicatorService().addIndicator(RSI, this.getRelativeStrengthIndex());
    this.getIndicatorService().addIndicator(RVI, this.getRelativeVigorIndex());
  }

  @Synchronized
  public void run() {
    if (this.getCandlestickService().isReady() && this.getLastDataTime().plusMinutes(this.getInterval())
        .isBefore(this.getCandlestickService().getOldestCandlestick().realDateTime())) {
      this.setLastDataTime(this.getCandlestickService().getOldestCandlestick().realDateTime().withSecond(0));
      this.getIndicatorService().getIndicators().values().stream().map(Thread::new).map(thread -> {
        thread.start();
        return thread;
      }).forEach(thread -> {
        try {
          thread.join();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IndicatorsException(e);
        }
      });
      final TreeMap<String, IndicatorTrend> trendSortedMap = this.getIndicatorService().getIndicators().entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, o -> o.getValue().getSignal(), (o1, o2) -> o1, TreeMap::new));
      final BigDecimal average = BigDecimal.valueOf(trendSortedMap.values().stream().mapToInt(indicator -> switch (indicator) {
        case SELL -> -1;
        case BUY -> 1;
        case NEUTRAL -> 0;
      }).sum()).divide(BigDecimal.valueOf(trendSortedMap.size()), 2, RoundingMode.DOWN);
      if (average.compareTo(BigDecimal.valueOf(-0.5)) > 0 && average.compareTo(BigDecimal.valueOf(0.5)) < 0) {
        this.getSignalService().addGlobalSignal(this.getCandlestickService().getOldestCandlestick(), SignalTrend.NEUTRAL, trendSortedMap);
      } else if (average.compareTo(BigDecimal.ONE) == 0) {
        this.getSignalService().addGlobalSignal(this.getCandlestickService().getOldestCandlestick(), SignalTrend.BUY, trendSortedMap);
      } else if (average.compareTo(BigDecimal.valueOf(-1)) == 0) {
        this.getSignalService().addGlobalSignal(this.getCandlestickService().getOldestCandlestick(), SignalTrend.SELL, trendSortedMap);
      } else if (average.compareTo(BigDecimal.ONE) > 0) {
        this.getSignalService().addGlobalSignal(this.getCandlestickService().getOldestCandlestick(), SignalTrend.STRONG_BUY, trendSortedMap);
      } else if (average.compareTo(BigDecimal.valueOf(-1)) < 0) {
        this.getSignalService().addGlobalSignal(this.getCandlestickService().getOldestCandlestick(), SignalTrend.STRONG_SELL, trendSortedMap);
      }
    }
  }
}

