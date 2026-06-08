package com.visionox.mes.lot.service;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.lot.dto.HoldRequest;
import com.visionox.mes.lot.dto.ReleaseRequest;
import com.visionox.mes.lot.entity.HoldRecord;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.mapper.HoldRecordMapper;
import com.visionox.mes.lot.mapper.LotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    @Mock
    private LotMapper lotMapper;

    @Mock
    private HoldRecordMapper holdRecordMapper;

    @InjectMocks
    private HoldService holdService;

    @Test
    void holdLotShouldRejectMissingLot() {
        HoldRequest request = holdRequest("LOT001");
        when(lotMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> holdService.holdLot(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LOT001");

        verify(lotMapper, never()).updateById(any());
        verify(holdRecordMapper, never()).insert(any());
    }

    @Test
    void holdLotShouldUpdateLotAndCreateActiveHoldRecord() {
        Lot lot = lot("LOT001", "READY", 0);
        HoldRequest request = holdRequest("LOT001");
        when(lotMapper.selectOne(any())).thenReturn(lot);

        holdService.holdLot(request);

        assertThat(lot.getStatus()).isEqualTo("HOLD");
        assertThat(lot.getHoldFlag()).isEqualTo(1);
        verify(lotMapper).updateById(lot);

        ArgumentCaptor<HoldRecord> captor = ArgumentCaptor.forClass(HoldRecord.class);
        verify(holdRecordMapper).insert(captor.capture());
        HoldRecord record = captor.getValue();
        assertThat(record.getLotNo()).isEqualTo("LOT001");
        assertThat(record.getHoldReason()).isEqualTo("质量异常");
        assertThat(record.getHoldType()).isEqualTo("QUALITY");
        assertThat(record.getHoldBy()).isEqualTo("qe1001");
        assertThat(record.getStatus()).isEqualTo("HOLD");
        assertThat(record.getHoldTime()).isNotNull();
    }

    @Test
    void releaseLotShouldRejectLotNotOnHold() {
        Lot lot = lot("LOT001", "READY", 0);
        when(lotMapper.selectOne(any())).thenReturn(lot);

        ReleaseRequest request = releaseRequest("LOT001");

        assertThatThrownBy(() -> holdService.releaseLot(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LOT001");

        verify(holdRecordMapper, never()).updateById(any());
        verify(lotMapper, never()).updateById(any());
    }

    @Test
    void releaseLotShouldCloseHoldRecordAndReturnLotToReady() {
        Lot lot = lot("LOT001", "HOLD", 1);
        HoldRecord holdRecord = new HoldRecord();
        holdRecord.setLotNo("LOT001");
        holdRecord.setStatus("HOLD");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(holdRecordMapper.selectOne(any())).thenReturn(holdRecord);

        holdService.releaseLot(releaseRequest("LOT001"));

        assertThat(holdRecord.getStatus()).isEqualTo("RELEASED");
        assertThat(holdRecord.getReleaseBy()).isEqualTo("qe1001");
        assertThat(holdRecord.getDisposition()).isEqualTo("复判OK，放行");
        assertThat(holdRecord.getReleaseTime()).isNotNull();
        verify(holdRecordMapper).updateById(holdRecord);

        assertThat(lot.getStatus()).isEqualTo("READY");
        assertThat(lot.getHoldFlag()).isEqualTo(0);
        verify(lotMapper).updateById(lot);
    }

    private HoldRequest holdRequest(String lotNo) {
        HoldRequest request = new HoldRequest();
        request.setLotNo(lotNo);
        request.setHoldReason("质量异常");
        request.setHoldType("QUALITY");
        request.setHoldBy("qe1001");
        return request;
    }

    private ReleaseRequest releaseRequest(String lotNo) {
        ReleaseRequest request = new ReleaseRequest();
        request.setLotNo(lotNo);
        request.setReleaseBy("qe1001");
        request.setDisposition("复判OK，放行");
        return request;
    }

    private Lot lot(String lotNo, String status, int holdFlag) {
        Lot lot = new Lot();
        lot.setLotNo(lotNo);
        lot.setStatus(status);
        lot.setHoldFlag(holdFlag);
        return lot;
    }
}
