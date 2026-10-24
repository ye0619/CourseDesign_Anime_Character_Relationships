package com.animegraph.viewer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ViewerServiceTest {
    private ViewerModels.Binding binding() {return new ViewerModels.Binding("id",1,"sai","Sai",null,"PUBLIC","revision","IDLE",0,0,true,null,null);}
    @Test void failedSyncNeverClearsTheLastSuccessfulSnapshot() throws Exception {
        var mapper=mock(ViewerMapper.class);var client=mock(BangumiClient.class);var done=new CountDownLatch(1);
        when(mapper.binding("id")).thenReturn(binding());when(mapper.start(anyString(),anyString())).thenReturn(1);
        when(client.collections(anyString(),anyInt(),isNull())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,"BANGUMI_UNAVAILABLE","网络暂时不可用"));
        doAnswer(call -> {done.countDown();return null;}).when(mapper).finish(eq("id"),eq("revision"),eq("FAILED"),anyString(),eq(true));
        var service=new ViewerService(mapper,client,new ObjectMapper(),mock(TransactionTemplate.class),new PreferenceService());
        try {
            service.sync("id",null);assertThat(done.await(3,TimeUnit.SECONDS)).isTrue();verify(mapper,never()).clear(anyString());
        } finally {service.close();}
    }
    @Test void unbindingDuringFetchPreventsAStaleWorkerFromWritingBack() throws Exception {
        var mapper=mock(ViewerMapper.class);var client=mock(BangumiClient.class);var fetching=new CountDownLatch(1);var proceed=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var current=new AtomicReference<>(binding());
        when(mapper.binding("id")).thenAnswer(call -> current.get());when(mapper.start(anyString(),anyString())).thenReturn(1);
        doAnswer(call -> {current.set(null);return null;}).when(mapper).unbind("id");
        when(client.collections(anyString(),anyInt(),isNull())).thenAnswer(call -> {fetching.countDown();proceed.await(3,TimeUnit.SECONDS);finished.countDown();return new ViewerModels.RemotePage(0,java.util.List.of());});
        var transactions=mock(TransactionTemplate.class);
        var service=new ViewerService(mapper,client,new ObjectMapper(),transactions,new PreferenceService());
        try {
            service.sync("id",null);assertThat(fetching.await(3,TimeUnit.SECONDS)).isTrue();service.unbind("id");proceed.countDown();assertThat(finished.await(3,TimeUnit.SECONDS)).isTrue();
            service.close();verify(transactions,never()).executeWithoutResult(any());verify(mapper,never()).clear(anyString());
        } finally {proceed.countDown();service.close();}
    }
    @Test void reauthorizingTheSameUserKeepsTheirExistingWatchlist() {
        var mapper=mock(ViewerMapper.class);when(mapper.binding("id")).thenReturn(binding());
        var transactions=mock(TransactionTemplate.class);
        doAnswer(call -> {Consumer<TransactionStatus> operation=call.getArgument(0);operation.accept(mock(TransactionStatus.class));return null;}).when(transactions).executeWithoutResult(any());
        var service=new ViewerService(mapper,mock(BangumiClient.class),new ObjectMapper(),transactions,new PreferenceService());
        try {
            service.bind("id",new ViewerModels.Profile(1,"sai","Sai",null),"TOKEN");
            verify(mapper).reauthorize(eq("id"),any(),eq("TOKEN"),anyString());verify(mapper,never()).clear(anyString());
        } finally {service.close();}
    }
}
