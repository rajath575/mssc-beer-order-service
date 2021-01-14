package guru.sfg.brewery.model.events;

import guru.sfg.brewery.model.BeerOrderDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AllocateBeerOrderRequest implements Serializable {

    private static final long serialVersionUID = 2413344861265141851L;

    private BeerOrderDto beerOrderDto;

}
