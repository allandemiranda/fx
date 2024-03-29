package br.eti.allandemiranda.forex.repositories;

import br.eti.allandemiranda.forex.dtos.AC;
import br.eti.allandemiranda.forex.entities.AcEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.TreeSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

@Repository
@Getter(AccessLevel.PRIVATE)
@Setter(AccessLevel.PRIVATE)
public class AcRepository {

  private static final int MEMORY_SIZE = 2;
  private final TreeSet<AcEntity> dataBase = new TreeSet<>();

  /**
   * To add an AC new value to the repository. If the date time exists, will replace the indicator value.
   *
   * @param dateTime The date time
   * @param ac       The indicator value
   */
  public void add(final @NotNull LocalDateTime dateTime, final @NotNull BigDecimal ac) {
    if (this.getDataBase().isEmpty() || dateTime.isAfter(this.getDataBase().first().getDateTime())) {
      final AcEntity entity = new AcEntity();
      entity.setDateTime(dateTime);
      entity.setValue(ac);
      if (!this.getDataBase().add(entity)) {
        this.getDataBase().remove(entity);
        this.getDataBase().add(entity);
      }
      if (this.getDataBase().size() > MEMORY_SIZE) {
        this.getDataBase().pollLast();
      }
    } else {
      this.getDataBase().first().setValue(ac);
    }
  }

  /**
   * Get database values
   *
   * @return Array of AC
   */
  public AC @NotNull [] get() {
    return this.getDataBase().stream().map(this::toModel).toArray(AC[]::new);
  }

  /**
   * Convert an entity in a model
   *
   * @param entity The entity to be converted
   * @return The model
   */
  private @NotNull AC toModel(final @NotNull AcEntity entity) {
    return new AC(entity.getDateTime(), entity.getValue());
  }
}
