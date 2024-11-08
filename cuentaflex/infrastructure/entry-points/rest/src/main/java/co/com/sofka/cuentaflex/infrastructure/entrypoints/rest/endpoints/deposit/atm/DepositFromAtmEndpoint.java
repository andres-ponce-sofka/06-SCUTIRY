package co.com.sofka.cuentaflex.infrastructure.entrypoints.rest.endpoints.deposit.atm;

import co.com.sofka.cuentaflex.domain.usecases.common.transactions.FeesValues;
import co.com.sofka.cuentaflex.domain.usecases.common.transactions.TransactionDoneResponse;
import co.com.sofka.cuentaflex.domain.usecases.common.transactions.TransactionErrors;
import co.com.sofka.cuentaflex.domain.usecases.common.transactions.UnidirectionalTransactionRequest;
import co.com.sofka.cuentaflex.domain.usecases.deposit.atm.DepositFromAtmUseCase;
import co.com.sofka.cuentaflex.infrastructure.entrypoints.rest.common.dtos.TransactionDoneDto;
import co.com.sofka.cuentaflex.infrastructure.entrypoints.rest.common.dtos.UnidirectionalTransactionDto;
import co.com.sofka.cuentaflex.infrastructure.entrypoints.rest.common.mappers.TransactionDoneMapper;
import co.com.sofka.cuentaflex.infrastructure.entrypoints.rest.common.mappers.UnidirectionalTransactionMapper;
import co.com.sofka.cuentaflex.infrastructure.entrypoints.rest.constants.AccountEndpointsConstants;
import co.com.sofka.shared.domain.usecases.ResultWith;
import co.com.sofka.shared.infrastructure.entrypoints.din.DinErrorMapper;
import co.com.sofka.shared.infrastructure.entrypoints.din.DinRequest;
import co.com.sofka.shared.infrastructure.entrypoints.din.DinResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(AccountEndpointsConstants.ATM_DEPOSIT_TO_ACCOUNT_URL)
@Tag(name = "Account Deposits")
public final class DepositFromAtmEndpoint {
    private final DepositFromAtmUseCase depositFromAtmUseCase;
    private final FeesValues feesValues;

    private static final Map<String, HttpStatus> ERROR_STATUS_MAP = new HashMap<>();

    static {
        ERROR_STATUS_MAP.put(TransactionErrors.ACCOUNT_NOT_FOUND.getCode(), HttpStatus.NOT_FOUND);
        ERROR_STATUS_MAP.put(TransactionErrors.INVALID_AMOUNT.getCode(), HttpStatus.BAD_REQUEST);
    }

    public DepositFromAtmEndpoint(DepositFromAtmUseCase depositFromAtmUseCase, FeesValues feesValues) {
        this.depositFromAtmUseCase = depositFromAtmUseCase;
        this.feesValues = feesValues;
    }

    @PostMapping
    public ResponseEntity<DinResponse<TransactionDoneDto>>  deposit(
            @RequestBody DinRequest<UnidirectionalTransactionDto> requestDto
    ) {
        UnidirectionalTransactionRequest request = UnidirectionalTransactionMapper.fromDinToUseCaseRequest(requestDto);

        ResultWith<TransactionDoneResponse> result = this.depositFromAtmUseCase.execute(request);

        if (result.isFailure()) {
            HttpStatus status = ERROR_STATUS_MAP.getOrDefault(
                    result.getError().getCode(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );

            return ResponseEntity.status(status).body(
                    DinErrorMapper.fromUseCaseToDinResponse(
                            requestDto.getDinHeader(),
                            result.getError(),
                            getErrorDetails(requestDto, result)
                    )
            );
        }

        return ResponseEntity.ok(
                TransactionDoneMapper.fromUseCaseToDinResponse(requestDto.getDinHeader(), result.getValue())
        );
    }

    private String[] getErrorDetails(DinRequest<UnidirectionalTransactionDto> requestDto, ResultWith<TransactionDoneResponse> result) {
        if(result.isSuccess()) {
            return new String[0];
        }

        if(result.getError().getCode().equals(TransactionErrors.INVALID_AMOUNT.getCode())) {
            return new String[] {this.feesValues.getDepositFromAtmFee().toString()};
        }

        if(result.getError().getCode().equals(TransactionErrors.ACCOUNT_NOT_FOUND.getCode())) {
            return new String[] {requestDto.getDinBody().getAccountId()};
        }

        return new String[0];
    }
}