package Helper;

import java.util.List;

public class Paginator<T> {

  private final List<T> allData;
  private final int pageSize;
  private int currentPage;

  public Paginator(List<T> allData, int pageSize) {
    this.allData = allData;
    this.pageSize = pageSize;
    this.currentPage = 0;
  }

  public List<T> getCurrentPage() {
    int from = currentPage * pageSize;
    int to = Math.min(from + pageSize, allData.size());
    if (from >= allData.size()) return List.of(); // Empty if out of bounds
    return allData.subList(from, to);
  }

  public boolean hasNextPage() {
    return (currentPage + 1) * pageSize < allData.size();
  }

  public boolean hasPreviousPage() {
    return currentPage > 0;
  }

  public void nextPage() {
    if (hasNextPage()) {
      currentPage++;
    }
  }

  public void previousPage() {
    if (hasPreviousPage()) {
      currentPage--;
    }
  }

  public void firstPage() {
    currentPage = 0;
  }

  public void lastPage() {
    currentPage = (allData.size() - 1) / pageSize;
  }

  public int getCurrentPageNumber() {
    return currentPage + 1; // 1-based for UI
  }

  public int getTotalPages() {
    return (int) Math.ceil((double) allData.size() / pageSize);
  }

  public int getPageSize() {
    return pageSize;
  }

  public int getTotalItems() {
    return allData.size();
  }
}
