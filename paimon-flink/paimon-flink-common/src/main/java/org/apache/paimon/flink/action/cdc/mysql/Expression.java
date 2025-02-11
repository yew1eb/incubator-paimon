/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.flink.action.cdc.mysql;

import org.apache.paimon.types.DataType;
import org.apache.paimon.types.DataTypes;
import org.apache.paimon.utils.DateTimeUtils;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.apache.paimon.utils.Preconditions.checkArgument;

/** Produce a computation result for computed column. */
public interface Expression extends Serializable {

    List<String> SUPPORTED_EXPRESSION = Arrays.asList("year", "substring");

    /** Return name of referenced field. */
    String fieldReference();

    /** Return {@link DataType} of computed value. */
    DataType outputType();

    /** Compute value from given input. Input and output are serialized to string. */
    String eval(String input);

    static Expression create(
            String exprName, String fieldReference, DataType fieldType, String... literals) {
        switch (exprName) {
            case "year":
                return year(fieldReference);
            case "substring":
                return substring(fieldReference, literals);
                // TODO: support more expression
            default:
                throw new UnsupportedOperationException(
                        String.format(
                                "Unsupported expression: %s. Supported expressions are: %s",
                                exprName, String.join(",", SUPPORTED_EXPRESSION)));
        }
    }

    static Expression year(String fieldReference) {
        return new YearComputer(fieldReference);
    }

    static Expression substring(String fieldReference, String... literals) {
        checkArgument(
                literals.length == 1 || literals.length == 2,
                String.format(
                        "'substring' expression supports one or two arguments, but found '%s'.",
                        literals.length));
        int beginInclusive;
        Integer endExclusive;
        try {
            beginInclusive = Integer.parseInt(literals[0]);
            endExclusive = literals.length == 1 ? null : Integer.parseInt(literals[1]);
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                    String.format(
                            "The index arguments '%s' contain non integer value.",
                            Arrays.toString(literals)),
                    e);
        }
        checkArgument(
                beginInclusive >= 0,
                "begin index argument (%s) of 'substring' must be >= 0.",
                beginInclusive);
        checkArgument(
                endExclusive == null || endExclusive > beginInclusive,
                "end index (%s) must be larger than begin index (%s).",
                endExclusive,
                beginInclusive);
        return new Substring(fieldReference, beginInclusive, endExclusive);
    }

    /** Compute year from a time input. */
    final class YearComputer implements Expression {

        private static final long serialVersionUID = 1L;

        private final String fieldReference;

        private YearComputer(String fieldReference) {
            this.fieldReference = fieldReference;
        }

        @Override
        public String fieldReference() {
            return fieldReference;
        }

        @Override
        public DataType outputType() {
            return DataTypes.INT();
        }

        @Override
        public String eval(String input) {
            LocalDateTime localDateTime = DateTimeUtils.toLocalDateTime(input, 0);
            return String.valueOf(localDateTime.getYear());
        }
    }

    /** Get substring using {@link String#substring}. */
    final class Substring implements Expression {

        private static final long serialVersionUID = 1L;

        private final String fieldReference;
        private final int beginInclusive;
        @Nullable private final Integer endExclusive;

        private Substring(
                String fieldReference, int beginInclusive, @Nullable Integer endExclusive) {
            this.fieldReference = fieldReference;
            this.beginInclusive = beginInclusive;
            this.endExclusive = endExclusive;
        }

        @Override
        public String fieldReference() {
            return fieldReference;
        }

        @Override
        public DataType outputType() {
            return DataTypes.STRING();
        }

        @Override
        public String eval(String input) {
            try {
                if (endExclusive == null) {
                    return input.substring(beginInclusive);
                } else {
                    return input.substring(beginInclusive, endExclusive);
                }
            } catch (StringIndexOutOfBoundsException e) {
                throw new RuntimeException(
                        String.format(
                                "Cannot get substring from '%s' because the indexes are out of range. Begin index: %s, end index: %s.",
                                input, beginInclusive, endExclusive));
            }
        }
    }
}
