import React from 'react';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  totalItems: number;
  itemsPerPage: number;
  onPageChange: (page: number) => void;
  onItemsPerPageChange?: (itemsPerPage: number) => void;
  itemLabel?: string;
}

const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  totalItems,
  itemsPerPage,
  onPageChange,
  onItemsPerPageChange,
  itemLabel = 'resultados'
}) => {
  const startItem = totalItems === 0 ? 0 : (currentPage - 1) * itemsPerPage + 1;
  const endItem = Math.min(currentPage * itemsPerPage, totalItems);

  if (totalItems === 0) {
    return null;
  }

  const showPaginationControls = totalPages > 1;

  const getVisiblePages = () => {
    const maxVisible = 10;
    if (totalPages <= maxVisible) {
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }

    const pages: (number | string)[] = [];
    
    if (currentPage <= 5) {
      for (let i = 1; i <= 7; i++) {
        pages.push(i);
      }
      pages.push('...');
      pages.push(totalPages);
    } else if (currentPage >= totalPages - 4) {
      pages.push(1);
      pages.push('...');
      for (let i = totalPages - 6; i <= totalPages; i++) {
        pages.push(i);
      }
    } else {
      pages.push(1);
      pages.push('...');
      for (let i = currentPage - 2; i <= currentPage + 2; i++) {
        pages.push(i);
      }
      pages.push('...');
      pages.push(totalPages);
    }

    return pages;
  };

  return (
    <div className="pagination-container-yego">
      <div className="pagination-info-yego">
        <span>Mostrando {startItem} a {endItem} de {totalItems} {itemLabel}</span>
        {onItemsPerPageChange && (
          <div className="pagination-items-per-page">
            <label htmlFor="items-per-page-select">Mostrar:</label>
            <select
              id="items-per-page-select"
              className="pagination-select"
              value={itemsPerPage}
              onChange={(e) => {
                const newItemsPerPage = parseInt(e.target.value, 10);
                onItemsPerPageChange(newItemsPerPage);
                onPageChange(1);
              }}
            >
              <option value={5}>5</option>
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={30}>30</option>
            </select>
          </div>
        )}
      </div>
      {showPaginationControls && (
        <div className="pagination-controls-yego">
          <button
            className="pagination-btn-yego"
            onClick={() => onPageChange(1)}
            disabled={currentPage === 1}
            aria-label="Primera página"
          >
            ««
          </button>
          <button
            className="pagination-btn-yego"
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 1}
            aria-label="Página anterior"
          >
            «
          </button>
          
          {getVisiblePages().map((pageNum, index) => {
            if (pageNum === '...') {
              return (
                <span key={`ellipsis-${index}`} className="pagination-ellipsis-yego">
                  ...
                </span>
              );
            }

            return (
              <button
                key={pageNum}
                className={`pagination-btn-yego ${currentPage === pageNum ? 'active' : ''}`}
                onClick={() => onPageChange(pageNum as number)}
                aria-label={`Ir a página ${pageNum}`}
                aria-current={currentPage === pageNum ? 'page' : undefined}
              >
                {pageNum}
              </button>
            );
          })}

          <button
            className="pagination-btn-yego"
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
            aria-label="Página siguiente"
          >
            »
          </button>
          <button
            className="pagination-btn-yego"
            onClick={() => onPageChange(totalPages)}
            disabled={currentPage === totalPages}
            aria-label="Última página"
          >
            »»
          </button>
        </div>
      )}
    </div>
  );
};

export default Pagination;

