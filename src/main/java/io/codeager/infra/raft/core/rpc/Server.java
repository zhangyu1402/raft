package io.codeager.infra.raft.core.rpc;

import com.google.protobuf.StringValue;
import io.codeager.infra.raft.core.LocalNode;
import io.codeager.infra.raft.core.StateMachine;
import io.codeager.infra.raft.core.entity.LogEntity;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.vote.*;

import java.io.IOException;


public class Server extends GreeterGrpc.GreeterImplBase {
    private io.grpc.Server server;
    private LocalNode node;

    public Server(LocalNode node) {
        this.node = node;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(this.node.getEndpoint().getPort()).addService(this).build().start();
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null)
            server.awaitTermination();
    }

    public void stop() {
        this.server.shutdown();
    }

    @Override
    public void askForVote(VoteRequest request, StreamObserver<VoteReply> responseObserver) {
        node.resetWaitTimer();
        boolean status = this.node.handleVoteRequest(request.getTerm());
        VoteReply voteReply = VoteReply.newBuilder().setStatus(status).build();
        responseObserver.onNext(voteReply);
        responseObserver.onCompleted();
    }

    @Override
    public void updateLog(UpdateLogRequest request, StreamObserver<UpdateLogReply> responseObserver) {
        node.resetWaitTimer();
        LogEntity logEntity = LogEntity.of(request.getLogEntry());
        boolean checkState = this.node.checkLog(logEntity, request.getId());
        UpdateLogReply updateLogReply;
        if (checkState) {
            this.node.recover(logEntity);
//            this.node.appendEntry(request.getIndex(), request.getTerm(), request.getEntry());
            updateLogReply = UpdateLogReply.newBuilder().setStatus(true).build();
        } else {

            updateLogReply = UpdateLogReply.newBuilder().setStatus(false).build();
        }
        responseObserver.onNext(updateLogReply);
        responseObserver.onCompleted();
    }

    @Override
    public void appendLog(UpdateLogRequest request, StreamObserver<UpdateLogReply> responseObserver) {
        if (request.hasEntry()) {
            this.node.appendEntry(LogEntity.of(request.getLogEntry()), request.getEntry().getValue());
        } else {
            this.node.appendEntry(LogEntity.of(request.getLogEntry()), null);
        }
        UpdateLogReply updateLogReply;
        updateLogReply = UpdateLogReply.newBuilder().setStatus(true).build();
        responseObserver.onNext(updateLogReply);
        responseObserver.onCompleted();
    }

    @Override
    public void store(StoreRequest request, StreamObserver<StoreResponse> responseObserver) {
        StoreResponse storeResponse;
        boolean status;
        if (this.node.getStateMachine().getState().role == StateMachine.Role.LEADER) {
            status = this.node.store(request.getEntry().getKey(), request.getEntry().getValue());
            storeResponse = StoreResponse.newBuilder().setStatus(status).build();
        } else {
            status = this.node.leader.store(request);
            storeResponse = StoreResponse.newBuilder().setStatus(status).build();
        }
        responseObserver.onNext(storeResponse);
        responseObserver.onCompleted();
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        String key = request.getKey();
        GetResponse getResponse;
        if (this.node.getStateMachine().getState().role == StateMachine.Role.LEADER) {
            String value = this.node.get(key);
            getResponse = GetResponse.newBuilder().setValue(StringValue.of(value)).build();

        } else {
            String value = this.node.leader.get(GetRequest.newBuilder().setKey(key).build());
            getResponse = GetResponse.newBuilder().setValue(StringValue.of(value)).build();
        }
        responseObserver.onNext(getResponse);
        responseObserver.onCompleted();
    }

    @Override
    public void size(SizeRequest request, StreamObserver<SizeResponse> responseObserver) {

        SizeResponse sizeResponse;
        if (this.node.getStateMachine().getState().role == StateMachine.Role.LEADER) {
            int size = this.node.size();
            sizeResponse = SizeResponse.newBuilder().setSize(size).build();
        } else {
            int size = this.node.leader.size(SizeRequest.newBuilder().build());
            sizeResponse = SizeResponse.newBuilder().setSize(size).build();
        }
        responseObserver.onNext(sizeResponse);
        responseObserver.onCompleted();
    }

    @Override
    public void remove(RemoveRequest request, StreamObserver<RemoveResponse> responseObserver) {
        RemoveResponse removeResponse;
        if (this.node.getStateMachine().getState().role == StateMachine.Role.LEADER) {
            boolean status = this.node.remove(request.getKey());
            removeResponse = RemoveResponse.newBuilder().setStatus(status).build();
        } else {
            boolean status = this.node.leader.remove(request);
            removeResponse = RemoveResponse.newBuilder().setStatus(status).build();
        }
        responseObserver.onNext(removeResponse);
        responseObserver.onCompleted();
    }

    public static void main(String... args) {

    }
}
