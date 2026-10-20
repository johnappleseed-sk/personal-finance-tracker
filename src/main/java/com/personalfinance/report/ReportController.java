package com.personalfinance.report;

import java.nio.charset.StandardCharsets;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.security.FinanceUserDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Serves the Management Information Systems (MIS) & Decision Support (DSS) analytics page and Excel CSV export. */
@Controller
@RequestMapping("/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping
	public String showReports(@AuthenticationPrincipal FinanceUserDetails user,
			@RequestParam(name = "currency", required = false) AccountCurrency currency,
			Model model) {
		ReportView report = reportService.buildReport(user.getUserId(), currency);
		model.addAttribute("report", report);
		return "reports/index";
	}

	@GetMapping(value = "/export-monthly.csv", produces = "text/csv")
	public ResponseEntity<byte[]> exportMonthlySummaryCsv(@AuthenticationPrincipal FinanceUserDetails user,
			@RequestParam(name = "currency", required = false) AccountCurrency currency) {
		String csv = reportService.exportMonthlySummaryCsv(user.getUserId(), currency);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mis-financial-report.csv\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(csv.getBytes(StandardCharsets.UTF_8));
	}
}
