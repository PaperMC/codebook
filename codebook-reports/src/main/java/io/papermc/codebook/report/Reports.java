/*
 * codebook is a remapper utility for the PaperMC project.
 *
 * Copyright (c) 2023 Kyle Wood (DenWav)
 *                    Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 3 only, no later versions.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package io.papermc.codebook.report;

import com.google.inject.AbstractModule;
import io.papermc.codebook.report.type.MissingMethodLvtSuggestion;
import io.papermc.codebook.report.type.Report;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Reports extends AbstractModule {

    @SuppressWarnings({"DataFlowIssue"})
    public static final Reports NOOP = new Reports(null, Set.of()) {
        @Override
        public void generateReports() {
            // NO-OP
        }

        @Override
        protected void configure() {
            // NO-OP
        }
    };

    private final Path reportsDir;
    private final Set<ReportType> typesToGenerate;
    private final Map<ReportType, Report> reports;

    public Reports(final Path reportsDir, final Set<ReportType> typesToGenerate) {
        this.reportsDir = reportsDir;
        this.typesToGenerate = typesToGenerate;
        this.reports = Map.of(ReportType.MISSING_METHOD_LVT_SUGGESTION, new MissingMethodLvtSuggestion());
    }

    public void generateReports() throws IOException {
        Files.createDirectories(this.reportsDir);
        for (final Entry<ReportType, Report> entry : this.reports.entrySet()) {
            if (this.typesToGenerate.contains(entry.getKey())) {
                final Path reportPath =
                        this.reportsDir.resolve(entry.getKey().name().toLowerCase(Locale.ENGLISH) + ".txt");
                Files.writeString(reportPath, entry.getValue().generate());
            }
        }
    }

    @Override
    protected void configure() {
        this.reports.values().forEach(this::bindReport);
    }

    @SuppressWarnings("unchecked")
    private <R extends Report> void bindReport(final R report) {
        this.bind((Class<R>) report.getClass()).toInstance(report);
    }
}
