/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.examples.quickstart;

import org.apache.hudi.common.fs.FSUtils;
import org.apache.hudi.examples.quickstart.utils.QuickstartConfigurations;

import org.apache.avro.generic.GenericRecord;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.data.binary.BinaryRowData;
import org.apache.flink.table.data.conversion.DataStructureConverter;
import org.apache.flink.table.data.conversion.DataStructureConverters;
import org.apache.flink.table.data.writer.BinaryRowWriter;
import org.apache.flink.table.data.writer.BinaryWriter;
import org.apache.flink.table.runtime.typeutils.InternalSerializers;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.Strings;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Data set for testing, also some utilities to check the results.
 */
public class TestQuickstartData {

  public static List<RowData> DATA_SET_INSERT_DUPLICATES = new ArrayList<>();

  static {
    IntStream.range(0, 5).forEach(i -> DATA_SET_INSERT_DUPLICATES.add(
        insertRow(StringData.fromString("id1"), StringData.fromString("Danny"), 23,
            TimestampData.fromEpochMillis(1), StringData.fromString("par1"))));
  }

  public static List<RowData> DATA_SET_INSERT_SAME_KEY = new ArrayList<>();

  static {
    IntStream.range(0, 5).forEach(i -> DATA_SET_INSERT_SAME_KEY.add(
        insertRow(StringData.fromString("id1"), StringData.fromString("Danny"), 23,
            TimestampData.fromEpochMillis(i), StringData.fromString("par1"))));
  }

  // data set of source-file.json latest commit.
  public static List<RowData> DATA_SET_SOURCE_INSERT_LATEST_COMMIT = Arrays.asList(
      insertRow(StringData.fromString("id5"), StringData.fromString("Sophia"), 18,
          TimestampData.fromEpochMillis(5000), StringData.fromString("par3")),
      insertRow(StringData.fromString("id6"), StringData.fromString("Emma"), 20,
          TimestampData.fromEpochMillis(6000), StringData.fromString("par3")),
      insertRow(StringData.fromString("id7"), StringData.fromString("Bob"), 44,
          TimestampData.fromEpochMillis(7000), StringData.fromString("par4")),
      insertRow(StringData.fromString("id8"), StringData.fromString("Han"), 56,
          TimestampData.fromEpochMillis(8000), StringData.fromString("par4"))
  );

  public static List<RowData> DATA_SET_DISORDER_UPDATE_DELETE = Arrays.asList(
      // DISORDER UPDATE
      updateAfterRow(StringData.fromString("id1"), StringData.fromString("Danny"), 21,
          TimestampData.fromEpochMillis(3), StringData.fromString("par1")),
      updateAfterRow(StringData.fromString("id1"), StringData.fromString("Danny"), 20,
          TimestampData.fromEpochMillis(2), StringData.fromString("par1")),
      updateBeforeRow(StringData.fromString("id1"), StringData.fromString("Danny"), 23,
          TimestampData.fromEpochMillis(1), StringData.fromString("par1")),
      updateBeforeRow(StringData.fromString("id1"), StringData.fromString("Danny"), 20,
          TimestampData.fromEpochMillis(2), StringData.fromString("par1")),
      updateAfterRow(StringData.fromString("id1"), StringData.fromString("Danny"), 22,
          TimestampData.fromEpochMillis(4), StringData.fromString("par1")),
      updateBeforeRow(StringData.fromString("id1"), StringData.fromString("Danny"), 21,
          TimestampData.fromEpochMillis(3), StringData.fromString("par1")),
      // DISORDER DELETE
      deleteRow(StringData.fromString("id1"), StringData.fromString("Danny"), 22,
          TimestampData.fromEpochMillis(2), StringData.fromString("par1"))
  );

  public static List<RowData> dataSetInsert(int... ids) {
    List<RowData> inserts = new ArrayList<>();
    Arrays.stream(ids).forEach(i -> inserts.add(
        insertRow(StringData.fromString("id" + i), StringData.fromString("Danny"), 23,
            TimestampData.fromEpochMillis(i), StringData.fromString("par1"))));
    return inserts;
  }

  private static Integer toIdSafely(Object id) {
    if (id == null) {
      return -1;
    }
    final String idStr = id.toString();
    if (idStr.startsWith("id")) {
      return Integer.parseInt(idStr.substring(2));
    }
    return -1;
  }

  /**
   * Returns string format of a list of RowData.
   */
  public static String rowDataToString(List<RowData> rows) {
    DataStructureConverter<Object, Object> converter =
        DataStructureConverters.getConverter(QuickstartConfigurations.ROW_DATA_TYPE);
    return rows.stream()
        .sorted(Comparator.comparing(o -> toIdSafely(o.getString(0))))
        .map(row -> converter.toExternal(row).toString())
        .collect(Collectors.toList()).toString();
  }

  private static String toStringSafely(Object obj) {
    return obj == null ? "null" : obj.toString();
  }

  /**
   * Sort the {@code rows} using field at index 0 and asserts
   * it equals with the expected string {@code expected}.
   *
   * @param rows     Actual result rows
   * @param expected Expected string of the sorted rows
   */
  public static void assertRowsEquals(List<Row> rows, String expected) {
    assertRowsEquals(rows, expected, false);
  }

  /**
   * Sort the {@code rows} using field at index 0 and asserts
   * it equals with the expected string {@code expected}.
   *
   * @param rows           Actual result rows
   * @param expected       Expected string of the sorted rows
   * @param withChangeFlag Whether compares with change flags
   */
  public static void assertRowsEquals(List<Row> rows, String expected, boolean withChangeFlag) {
    String rowsString = rows.stream()
        .sorted(Comparator.comparing(o -> toStringSafely(o.getField(0))))
        .map(row -> {
          final String rowStr = row.toString();
          if (withChangeFlag) {
            return row.getKind().shortString() + "(" + rowStr + ")";
          } else {
            return rowStr;
          }
        })
        .collect(Collectors.toList()).toString();
    assertThat(rowsString, is(expected));
  }

  /**
   * Sort the {@code rows} using field at index {@code orderingPos} and asserts
   * it equals with the expected string {@code expected}.
   *
   * @param rows        Actual result rows
   * @param expected    Expected string of the sorted rows
   * @param orderingPos Field position for ordering
   */
  public static void assertRowsEquals(List<Row> rows, String expected, int orderingPos) {
    String rowsString = rows.stream()
        .sorted(Comparator.comparing(o -> toStringSafely(o.getField(orderingPos))))
        .collect(Collectors.toList()).toString();
    assertThat(rowsString, is(expected));
  }

  /**
   * Sort the {@code rows} using field at index 0 and asserts
   * it equals with the expected row data list {@code expected}.
   *
   * @param rows     Actual result rows
   * @param expected Expected row data list
   */
  public static void assertRowsEquals(List<Row> rows, List<RowData> expected) {
    String rowsString = rows.stream()
        .sorted(Comparator.comparing(o -> toIdSafely(o.getField(0))))
        .collect(Collectors.toList()).toString();
    assertThat(rowsString, is(rowDataToString(expected)));
  }

  /**
   * Sort the {@code rows} using field at index 0 and asserts
   * it equals with the expected string {@code expected}.
   *
   * @param rows     Actual result rows
   * @param expected Expected string of the sorted rows
   */
  public static void assertRowDataEquals(List<RowData> rows, String expected) {
    String rowsString = rowDataToString(rows);
    assertThat(rowsString, is(expected));
  }

  /**
   * Sort the {@code rows} using field at index 0 and asserts
   * it equals with the expected row data list {@code expected}.
   *
   * @param rows     Actual result rows
   * @param expected Expected row data list
   */
  public static void assertRowDataEquals(List<RowData> rows, List<RowData> expected) {
    String rowsString = rowDataToString(rows);
    assertThat(rowsString, is(rowDataToString(expected)));
  }

  /**
   * Checks the source data set are written as expected.
   *
   * <p>Note: Replace it with the Flink reader when it is supported.
   *
   * @param baseFile The file base to check, should be a directory
   * @param expected The expected results mapping, the key should be the partition path
   *                 and value should be values list with the key partition
   */
  public static void checkWrittenData(File baseFile, Map<String, String> expected) throws IOException {
    checkWrittenData(baseFile, expected, 4);
  }

  /**
   * Checks the source data set are written as expected.
   *
   * <p>Note: Replace it with the Flink reader when it is supported.
   *
   * @param baseFile   The file base to check, should be a directory
   * @param expected   The expected results mapping, the key should be the partition path
   *                   and value should be values list with the key partition
   * @param partitions The expected partition number
   */
  public static void checkWrittenData(
      File baseFile,
      Map<String, String> expected,
      int partitions) throws IOException {
    assert baseFile.isDirectory();
    FileFilter filter = file -> !file.getName().startsWith(".");
    File[] partitionDirs = baseFile.listFiles(filter);
    assertNotNull(partitionDirs);
    assertThat(partitionDirs.length, is(partitions));
    for (File partitionDir : partitionDirs) {
      File[] dataFiles = partitionDir.listFiles(filter);
      assertNotNull(dataFiles);
      File latestDataFile = Arrays.stream(dataFiles)
          .max(Comparator.comparing(f -> FSUtils.getCommitTime(f.getName())))
          .orElse(dataFiles[0]);
      ParquetReader<GenericRecord> reader = AvroParquetReader
          .<GenericRecord>builder(new Path(latestDataFile.getAbsolutePath())).build();
      List<String> readBuffer = new ArrayList<>();
      GenericRecord nextRecord = reader.read();
      while (nextRecord != null) {
        readBuffer.add(filterOutVariables(nextRecord));
        nextRecord = reader.read();
      }
      readBuffer.sort(Comparator.naturalOrder());
      assertThat(readBuffer.toString(), is(expected.get(partitionDir.getName())));
    }
  }

  /**
   * Filter out the variables like file name.
   */
  private static String filterOutVariables(GenericRecord genericRecord) {
    List<String> fields = new ArrayList<>();
    fields.add(genericRecord.get("_hoodie_record_key").toString());
    fields.add(genericRecord.get("_hoodie_partition_path").toString());
    fields.add(genericRecord.get("uuid").toString());
    fields.add(genericRecord.get("name").toString());
    fields.add(genericRecord.get("age").toString());
    fields.add(genericRecord.get("ts").toString());
    fields.add(genericRecord.get("partition").toString());
    return Strings.join(fields, ",");
  }

  public static BinaryRowData insertRow(Object... fields) {
    return insertRow(QuickstartConfigurations.ROW_TYPE, fields);
  }

  public static BinaryRowData insertRow(RowType rowType, Object... fields) {
    LogicalType[] types = rowType.getFields().stream().map(RowType.RowField::getType)
        .toArray(LogicalType[]::new);
    assertEquals(
        "Filed count inconsistent with type information",
        fields.length,
        types.length);
    BinaryRowData row = new BinaryRowData(fields.length);
    BinaryRowWriter writer = new BinaryRowWriter(row);
    writer.reset();
    for (int i = 0; i < fields.length; i++) {
      Object field = fields[i];
      if (field == null) {
        writer.setNullAt(i);
      } else {
        BinaryWriter.write(writer, i, field, types[i], InternalSerializers.create(types[i]));
      }
    }
    writer.complete();
    return row;
  }

  private static BinaryRowData deleteRow(Object... fields) {
    BinaryRowData rowData = insertRow(fields);
    rowData.setRowKind(RowKind.DELETE);
    return rowData;
  }

  private static BinaryRowData updateBeforeRow(Object... fields) {
    BinaryRowData rowData = insertRow(fields);
    rowData.setRowKind(RowKind.UPDATE_BEFORE);
    return rowData;
  }

  private static BinaryRowData updateAfterRow(Object... fields) {
    BinaryRowData rowData = insertRow(fields);
    rowData.setRowKind(RowKind.UPDATE_AFTER);
    return rowData;
  }
}
