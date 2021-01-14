package guru.sfg.beer.order.service.sm;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.services.BeerOrderManagerImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeerOrderStateChangeInterceptor extends StateMachineInterceptorAdapter<BeerOrderStatusEnum, BeerOrderEventEnum> {

    private final BeerOrderRepository beerOrderRepository;

    @Transactional
    @Override
    public void preStateChange(State<BeerOrderStatusEnum, BeerOrderEventEnum> state,
                               Message<BeerOrderEventEnum> message,
                               Transition<BeerOrderStatusEnum, BeerOrderEventEnum> transition,
                               StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachine) {

        //fetch beer order id
        if (null != message
            && null != message.getHeaders().get(BeerOrderManagerImpl.BEER_ORDER_ID_HEADER)) {
            UUID beerId = (UUID) message.getHeaders().get(BeerOrderManagerImpl.BEER_ORDER_ID_HEADER);
            log.debug("Setting state for order id {} to state {}", beerId, state.getId());
            //fetch beer order
            Optional<BeerOrder> orderOptional = beerOrderRepository.findById(beerId);
            orderOptional.ifPresent(beerOrder -> {
                log.info("Set state for order {} to {}", beerId, state.getId());
                beerOrder.setOrderStatus(state.getId());
                // save state to repo
                beerOrderRepository.saveAndFlush(beerOrder);
            });

        }
    }
}
