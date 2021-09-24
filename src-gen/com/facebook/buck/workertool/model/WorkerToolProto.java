// @generated
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: src/com/facebook/buck/workertool/resources/proto/worker_tool.proto

package com.facebook.buck.workertool.model;

@javax.annotation.Generated(value="protoc", comments="annotations:WorkerToolProto.java.pb.meta")
public final class WorkerToolProto {
  private WorkerToolProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_workertool_api_v1_CommandTypeMessage_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_workertool_api_v1_CommandTypeMessage_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_workertool_api_v1_ExecuteCommand_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_workertool_api_v1_ExecuteCommand_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_workertool_api_v1_StartPipelineCommand_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_workertool_api_v1_StartPipelineCommand_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_workertool_api_v1_StartNextPipeliningCommand_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_workertool_api_v1_StartNextPipeliningCommand_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_workertool_api_v1_ShutdownCommand_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_workertool_api_v1_ShutdownCommand_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\nBsrc/com/facebook/buck/workertool/resou" +
      "rces/proto/worker_tool.proto\022\021workertool" +
      ".api.v1\"\344\001\n\022CommandTypeMessage\022G\n\014comman" +
      "d_type\030\001 \001(\01621.workertool.api.v1.Command" +
      "TypeMessage.CommandType\"\204\001\n\013CommandType\022" +
      "\013\n\007UNKNOWN\020\000\022\023\n\017EXECUTE_COMMAND\020\001\022\032\n\026STA" +
      "RT_PIPELINE_COMMAND\020\002\022!\n\035START_NEXT_PIPE" +
      "LINING_COMMAND\020\003\022\024\n\020SHUTDOWN_COMMAND\020\004\"#" +
      "\n\016ExecuteCommand\022\021\n\taction_id\030\001 \001(\t\")\n\024S" +
      "tartPipelineCommand\022\021\n\taction_id\030\001 \003(\t\"." +
      "\n\032StartNextPipeliningCommand\022\020\n\010actionId" +
      "\030\001 \001(\t\"\021\n\017ShutdownCommandB7\n\"com.faceboo" +
      "k.buck.workertool.modelB\017WorkerToolProto" +
      "P\001b\006proto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_workertool_api_v1_CommandTypeMessage_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_workertool_api_v1_CommandTypeMessage_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_workertool_api_v1_CommandTypeMessage_descriptor,
        new java.lang.String[] { "CommandType", });
    internal_static_workertool_api_v1_ExecuteCommand_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_workertool_api_v1_ExecuteCommand_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_workertool_api_v1_ExecuteCommand_descriptor,
        new java.lang.String[] { "ActionId", });
    internal_static_workertool_api_v1_StartPipelineCommand_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_workertool_api_v1_StartPipelineCommand_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_workertool_api_v1_StartPipelineCommand_descriptor,
        new java.lang.String[] { "ActionId", });
    internal_static_workertool_api_v1_StartNextPipeliningCommand_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_workertool_api_v1_StartNextPipeliningCommand_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_workertool_api_v1_StartNextPipeliningCommand_descriptor,
        new java.lang.String[] { "ActionId", });
    internal_static_workertool_api_v1_ShutdownCommand_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_workertool_api_v1_ShutdownCommand_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_workertool_api_v1_ShutdownCommand_descriptor,
        new java.lang.String[] { });
  }

  // @@protoc_insertion_point(outer_class_scope)
}