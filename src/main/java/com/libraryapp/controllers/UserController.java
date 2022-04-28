package com.libraryapp.controllers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.libraryapp.entities.Book;
import com.libraryapp.entities.Notification;
import com.libraryapp.entities.User;
import com.libraryapp.security.CurrentUserFinder;
import com.libraryapp.services.BookService;
import com.libraryapp.services.NotificationService;
import com.libraryapp.services.UserService;
import com.libraryapp.utils.DateTracker;
import com.libraryapp.utils.FineCalculator;
import com.libraryapp.utils.ListInStringConverter;

@Controller
@RequestMapping("/user")
public class UserController {
	
	
	@Autowired
	UserService usService;
	
	@Autowired
	BookService bookService;
	
	@Autowired
	CurrentUserFinder currentUserFinder;
	
	@Autowired
	FineCalculator fineCalculator;
	
	@Autowired
	DateTracker dateTracker;
	
	@Autowired
	NotificationService notifService;
	
	
	@Autowired
	ListInStringConverter listConverter;
	
	private int maximumWeeksToExtend = 3;
	
	@GetMapping
	public String userHome(Model model) {
		User currentUser = currentUserFinder.getCurrentUser();
		model.addAttribute("booksWithFines", fineCalculator.selectBooksWithFines(currentUser.getBooks()));
		model.addAttribute("currentUser", currentUser);
		return "user/user-home.html";
	}
	
	@GetMapping(value="/yourbooks")
	public String yourBooks(Model model) {
		User currentUser = currentUserFinder.getCurrentUser();
		List<Book> books = currentUser.getBooks();
		LinkedHashMap<Book, BigDecimal> booksWithFines = fineCalculator.getBooksWithFines(books);
		model.addAttribute("books", booksWithFines);
		return "user/user-your-books.html";
	}
	
	@PutMapping(value="/yourbooks/extend")
	public String extendRequest(@RequestParam BigDecimal fineAmount, 
								@RequestParam Long bookId,
								@RequestParam int weeksToExtend,
								Model model) {
		
		Book book = bookService.findById(bookId);
		int extensionsLeft = maximumWeeksToExtend - book.getTimesExtended();
		long daysTooLate = dateTracker.daysTooLate(book.getReturnDate());
		
		if (book.getTimesExtended() < maximumWeeksToExtend && fineAmount.compareTo(BigDecimal.valueOf(0)) == 0 && book.getReservedByUser() == null) {	
			book.setReturnDate(book.getReturnDate().plusDays(7 * weeksToExtend));
			book.setTimesExtended(book.getTimesExtended() + weeksToExtend);
			bookService.save(book);	
			return"redirect:/user/yourbooks/bookextended";
			
		} else if (fineAmount.compareTo(BigDecimal.valueOf(0)) == 1 && daysTooLate <= (extensionsLeft * 7) && book.getReservedByUser() == null) {
			return "redirect:/user/yourbooks/payfine/" + bookId;
		
		} else {
			return "redirect:/user/yourbooks/bookcannotbeextended";
		
		}
	}
	
	@GetMapping(value="/yourbooks/payfine/{bookId}")
	public String payFine(Model model, @PathVariable (value="bookId") Long bookId) {
		
		Book book = bookService.findById(bookId);
		BigDecimal fine = fineCalculator.getFineOfBook(book);	
		int weeksToExtend = dateTracker.getWeeksToExtendReturnDate(book);
			
		model.addAttribute("weeksToExtend", weeksToExtend);
		model.addAttribute("fine", fine);
		model.addAttribute("book", book);
		
		return "user/user-pay-fine.html";
	}
	
	@PostMapping(value="/yourbooks/dopayment")
	public String doPayment(@RequestParam int weeksToExtend,
							@RequestParam BigDecimal fineAmount,
							@RequestParam long bookId,
							Model model) {
		Book currentBook = bookService.findById(bookId);
		model.addAttribute("fineAmount", fineAmount);
		model.addAttribute("weeksToExtend", weeksToExtend);
		model.addAttribute("book", currentBook);
		return "user/user-do-payment.html";
	}
		
	@GetMapping(value="/yourbooks/bookextended")
	public String bookExtended() {
		return "user/user-book-extended.html";
	}
	
	@GetMapping(value="/yourbooks/bookcannotbeextended")
	public String bookCanNotBeExtended() {
		return "user/user-book-can-not-be-extended.html";
	}
	
	@GetMapping(value="/browsebooks")
	public String browseBooks(@RequestParam (required=false) String title,
							  @RequestParam (required=false) String author,
							  @RequestParam (required=false) String showAllBooks,
							  @RequestParam (required=false) Long  reservedBookId,
							  @RequestParam (required=false) Long removeBookId,
							  @RequestParam (required=false) String reservedBookIdsInString,
							  Model model) {
	
		Set<Long> reservedBookIds = new LinkedHashSet<Long>();
		if (reservedBookIdsInString != null) reservedBookIds = listConverter.convertListInStringToSetInLong(reservedBookIdsInString);		
		if (removeBookId != null) reservedBookIds.remove(removeBookId);
		if(reservedBookId != null) reservedBookIds.add(reservedBookId);
		
		Map<Book, String> listedBookReservations = dateTracker.listedBookReservations(reservedBookIds);
						
		List<Book> books;
		if (showAllBooks == null) books = bookService.searchBooks(title, author);
		else books = bookService.findAll();		
						
		model.addAttribute("userHasFine", fineCalculator.hasFineOrNot(currentUserFinder.getCurrentUser()));
		model.addAttribute("listedBookReservations", listedBookReservations);
		model.addAttribute("reservedBookIds", reservedBookIds);
		model.addAttribute("title", title);
		model.addAttribute("author", author);
		model.addAttribute("showAllBooks", showAllBooks);
		model.addAttribute("books", books);
		return "user/user-browse-books.html";
	}
	
	@GetMapping(value="/FAQ")
	public String FAQ() {
		return "user/user-FAQ.html";
	}
	
	@GetMapping(value="/ABHI")
	public String ABHI() {
		return "user/user-ABHI.html";
	}
	
	
	@GetMapping(value="/addbook")
	public String newBook(Model model) {
		model.addAttribute("book", new Book());
		return "user/user-new-book.html";
	}
	
	@PostMapping(value="/addbook/save")
	public String saveBook(Book book) {
		bookService.save(book);
		return "redirect:/user/addbook/booksaved";
	}
	
	@GetMapping(value="/addbook/booksaved")
	public String bookSaved() {
		return "user/user-book-saved.html";
	}
	
//	@GetMapping(value="/browsebooks/areyousuretodeletebook")
//	public String areYouSureToDeleteBook(@RequestParam Long deleteBookId, Model model) {
//		Book book = bookService.findById(deleteBookId);
//		model.addAttribute("deleteBook", book);
//		return "user/user-delete-book.html";
//	}
//	
//	@DeleteMapping(value="/browsebooks/deletebook")
//	public String deleteBook(@RequestParam Long deleteBookId) {
//		bookService.deleteById(deleteBookId);
//		return "redirect:/user/browsebooks/bookdeleted";
//	}
//	
//	@GetMapping(value="/browsebooks/bookdeleted")
//	public String bookDeleted() {
//		return "user/user-book-deleted.html";
//	}
	
	
	@PutMapping(value="/browsebooks/payreservation")
	public String payReservation(@RequestParam String reservedBookIdsInString,
								 @RequestParam Double amountToPay, 
								 Model model) {
		
		model.addAttribute("amountToPay", amountToPay);
		model.addAttribute("reservedBookIdsInString", reservedBookIdsInString);	
		return "user/user-pay-reservation.html";
	}
	
	@PutMapping(value="browsebooks/savereservation")
	public String saveBookReservations(@RequestParam String reservedBookIdsInString) {
		Set<Long> reservedBookIds = listConverter.convertListInStringToSetInLong(reservedBookIdsInString);
		dateTracker.setReserervationDatesAndReservedByCurrentUserForMultipleBooks(reservedBookIds);		
		return "redirect:/user/yourreservations";
	}
	
	@GetMapping(value="/yourreservations")
	public String yourReservations(Model model) {
		User currentUser = currentUserFinder.getCurrentUser();
		model.addAttribute("reservedBooks", currentUser.getReservedBooks());
		return "user/user-your-reservations.html";
	}
	
	@GetMapping(value="/reservations")
	public String reservations(Model model) {
		model.addAttribute("unprocessedReservations", bookService.getUnprocessedBookReservations());
		model.addAttribute("processedReservations", bookService.getProcessedBookReservations());
		return "user/user-reservations.html";
	}
	

	
	@PutMapping(value="/setreadyforpickup")
	public String setReadyForPickup(@RequestParam Long bookId, 
									@RequestParam Long userId,
									Model model) {
		model.addAttribute("user", usService.findById(userId));
		model.addAttribute("book", bookService.findById(bookId));
		return "user/user-reservation-ready-for-pick-up.html";
	}
	
	@PutMapping(value="/updatebookreservation")
	public String updateBookReservation(@RequestParam Long bookId,
										@RequestParam Long userId) {
		
		Book book = bookService.findById(bookId);
		Notification notification = new Notification(LocalDate.now(), book.getEndReservationDate(), "Your reservation is ready for pick-up until " + 
													book.getEndReservationDate() + ". " + book.getTitle() + " by " + book.getAuthor() + "."); 
											
		notification.setValidUntilDate(book.getEndReservationDate());
		notification.setNotificationReceiver(usService.findById(userId));
		notifService.save(notification);
		usService.saveById(userId);
		book.setReadyForPickup(true);
		bookService.save(book);
		return "redirect:/user/reservations";
	}
}
